package com.beneklund.minecraft.renderer;

import static com.beneklund.minecraft.util.Log.RENDER;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.GpuTimer;
import com.beneklund.minecraft.platform.graphics.ShadowFramebuffer;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import com.beneklund.minecraft.renderer.asset.ShaderProgram;
import com.beneklund.minecraft.renderer.asset.TextureAtlas;
import com.beneklund.minecraft.renderer.camera.Camera;
import com.beneklund.minecraft.renderer.camera.ShadowCamera;
import com.beneklund.minecraft.renderer.sky.CloudRenderer;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.EngineStats;
import com.beneklund.minecraft.world.sky.PreethamSky;
import com.beneklund.minecraft.world.sky.SkyModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Sequences the frame's passes and owns every GL state change between them. Renderables only
 * describe what to draw, as {@link DrawCall}s; this class decides when and how.
 *
 * <p>{@link #drawScene} runs, in order: the shadow cascades into {@link ShadowFramebuffer}, the
 * cloud march into its own buffer, then the opaque and transparent passes into the HDR scene
 * target. Every call from every renderable is collected before any pass draws, so transparent
 * geometry blends against the complete opaque scene. Post-processing follows outside this class,
 * then {@link #drawHud} draws the HUD untonemapped into the window.
 *
 * <p>Transparent geometry draws with the depth test on and depth writes off, unsorted. Surfaces
 * behind other transparent surfaces still draw; overlapping transparent surfaces blend in
 * submission order, which is exact only for one layer.
 *
 * <p>Frame-wide uniforms (matrices, sun, sky coefficients, cascade data) are built once per frame
 * in {@code setUniforms} and uploaded once per program per frame by {@link ShaderProgram#apply}.
 * {@code submit} skips a program or atlas bind when the previous call in the pass used the same
 * one.
 *
 * <p>{@link CloudRenderer} sits outside the {@link IRenderable} list because its pass must finish
 * before the sky samples it, so {@link #delete()} and {@link #reloadAll()} name it separately.
 *
 * @see <a href="https://learnopengl.com/Advanced-OpenGL/Blending">LearnOpenGL: Blending</a>
 * @see <a href="https://wikis.khronos.org/opengl/Transparency_Sorting">OpenGL Wiki: Transparency
 *     Sorting</a>
 */
public class Renderer {
    private final List<IRenderable> registered;
    private Color fogColor;
    private Vector3f sunDirection;
    private float skyBrightness;
    private float time;

    // Frame ordinal, pushed in from Game each frame. Only the GPU timer reads it: the query
    // rotation needs to know which frame is writing so it can read one from two frames back.
    private long frame;

    public static final int TIMER_SHADOW_C0 = 0;
    public static final int TIMER_SHADOW_C1 = 1;
    public static final int TIMER_SHADOW_C2 = 2;
    public static final int TIMER_CLOUDS = 3;
    public static final int TIMER_OPAQUE = 4;
    public static final int TIMER_TRANSPARENT = 5;
    public static final int TIMER_POST = 6;
    public static final int TIMER_PASS_COUNT = 7;

    /**
     * The GPU timer slot for one cascade. The cascade constants are contiguous, so this is index
     * arithmetic.
     *
     * <p>A fourth cascade needs a fourth constant here before {@link ShadowCamera} grows one. The
     * range check exists because the failure is silent: cascade 3 would land on {@code
     * TIMER_CLOUDS} and the clouds would appear to have taken 10 ms.
     *
     * @throws IllegalArgumentException if no slot is reserved for {@code cascade}
     */
    public static int timerForCascade(int cascade) {
        if (cascade < 0 || TIMER_SHADOW_C0 + cascade > TIMER_SHADOW_C2) {
            throw new IllegalArgumentException("no timer slot reserved for cascade " + cascade);
        }
        return TIMER_SHADOW_C0 + cascade;
    }

    // Null when gputimer.enabled is off, which is the normal case.
    private GpuTimer gpuTimer;

    private static final float TURBIDITY = 2.5f;
    private static final float EXTINCTION = (float) 1 / 350;
    public static final float EXPOSURE = 0.115f;

    // Seeded with the sun overhead so the coefficient uniforms are never null if a frame draws
    // before Game.run pushes a real sun position. Rebuilt in place by setSunDirection rather
    // than reallocated - this runs every frame.
    private final SkyModel sky = new SkyModel(TURBIDITY, EXPOSURE, new Vector3f(0.0f, 1.0f, 0.0f));

    private Vector3f coefficientA;
    private Vector3f coefficientB;
    private Vector3f coefficientC;
    private Vector3f coefficientD;
    private Vector3f coefficientE;
    private Vector3f skyZenith;
    private Vector3f skyZenithF;

    private final Matrix4f viewRotation = new Matrix4f();
    private final Matrix4f invViewRotation = new Matrix4f();
    private final Matrix4f invViewProj = new Matrix4f();
    // Unit 0 is the block atlas, bound by submit() from the DrawCall. The shadow map needs a unit
    // of its own or the depth lookup samples the atlas instead — samplers cannot ride in
    // frameUniforms, because UniformValue is sealed over float/vec3/mat4 with no int variant.
    private static final int SHADOW_TEXTURE_UNIT = 1;

    // Same story for the cloud buffer, which sky.frag reads back after CloudRenderer has marched
    // into it. One more unit rather than sharing 1: both are bound for every call, and a shader
    // that wanted both would otherwise get whichever was bound last.
    private static final int CLOUD_TEXTURE_UNIT = 2;

    private final Map<String, UniformValue<?>> frameUniforms = new HashMap<>();

    /*
     * What submit() last bound, so a run of calls sharing a program and an atlas costs one bind
     * instead of one per draw. The chunk calls arrive in one run from ChunkRenderer, so in practice
     * this collapses 1,157 opaque binds to one.
     *
     * null means "unknown, bind whatever comes next". Both are cleared at the top of every pass,
     * and that is not belt-and-braces: CloudRenderer, PostProcessor and the HUD shaders all bind
     * programs and textures of their own between passes, so a guard carried across a pass boundary
     * would skip a bind that is genuinely needed and draw with whatever they left behind.
     */
    private ShaderProgram boundShader;
    private Optional<TextureAtlas> boundAtlas;

    // Collected by drawScene, filtered again by drawHud. Render-thread-only, like everything here.
    private final List<DrawCall> calls = new ArrayList<>();

    private final ShadowFramebuffer shadowBuffer;

    // The cloud pass and the quarter-resolution buffer it marches into. Owned here rather than by
    // Game because the ordering constraint is the same one drawScene already exists to hold: this
    // has to run and finish before the sky pass reads it, and only one place can sequence that.
    private final CloudRenderer cloudRenderer;
    private final GlFramebuffer cloudBuffer;

    // The sun's projection. Everything about where the shadow map is rendered from lives there,
    // deliberately out of reach of the window and the view matrix — see the note on the class.
    private final ShadowCamera shadowCamera;

    /*
     * What each cascade's depth layer was last rendered from. A cascade whose matrix and world are
     * both unchanged still holds a correct image, so it is skipped — see drawShadowPass.
     *
     * A supplier rather than the RenderWorld itself: renderer/ has no business reaching into
     * infra/, and "how many times has the world changed" is the only thing needed here.
     */
    private final IntSupplier worldVersion_;

    // Which optional passes this build of the frame runs. Fixed at construction: it comes from
    // local.properties, which is read once at startup.
    private final RenderFeatures features;

    private final Matrix4f[] lastCascadeMatrix = new Matrix4f[ShadowCamera.cascadeCount()];
    private final int[] lastWorldVersion = new int[ShadowCamera.cascadeCount()];

    public Renderer(
            List<IRenderable> registered,
            Color fogColor,
            ShadowFramebuffer shadowBuffer,
            ShadowCamera shadowCamera,
            CloudRenderer cloudRenderer,
            GlFramebuffer cloudBuffer,
            IntSupplier worldVersion,
            RenderFeatures features) {
        this.registered = registered;
        this.features = features;
        this.fogColor = fogColor;
        this.shadowBuffer = shadowBuffer;
        this.shadowCamera = shadowCamera;
        this.cloudRenderer = cloudRenderer;
        this.cloudBuffer = cloudBuffer;
        worldVersion_ = worldVersion;
        for (int i = 0; i < lastCascadeMatrix.length; i++) {
            lastCascadeMatrix[i] = new Matrix4f();
            // Not 0: the world legitimately starts at version 0, and a cascade must render once
            // before it can be skipped or the first frames sample an uninitialised depth layer.
            lastWorldVersion[i] = -1;
        }
        // Seeded so the sky uniforms are never null if a frame draws before Game.run pushes
        // the real sun position. The sky shader resolves them to a real location, so unlike
        // the chunk shader it would NPE rather than no-op.
        setSunDirection(new Vector3f(0.0f, 1.0f, 0.0f));
    }

    public void delete() {
        RENDER.debug("deleting {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.delete();
        shadowBuffer.delete();
        cloudRenderer.delete();
    }

    /**
     * Rebuilds every shader from source; bound to the in-game reload action. Names {@code
     * cloudRenderer} separately because it isn't in the renderable list, and missing it reloads
     * every shader except the clouds.
     */
    public void reloadAll() {
        RENDER.info("reloading {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.reload();
        cloudRenderer.reload();
    }

    public void setSkyBrightness(float skyBrightness) {
        this.skyBrightness = skyBrightness;
        fogColor = Color.FOG.scale(skyBrightness);
    }

    public void setSunDirection(Vector3f sunDirection) {
        this.sunDirection = sunDirection;
        sky.setSunDirection(sunDirection);
        PreethamSky daylight = sky.preetham();
        coefficientA = daylight.coefficientA();
        coefficientB = daylight.coefficientB();
        coefficientC = daylight.coefficientC();
        coefficientD = daylight.coefficientD();
        coefficientE = daylight.coefficientE();
        skyZenith = daylight.zenith();
        skyZenithF = daylight.zenithF();
    }

    /**
     * Updates the cascade matrices. {@link ShadowCamera} takes the eye position and sun direction
     * and never the look direction, so shadows can't move with the mouse.
     */
    private void updateLightMatrix(Camera camera) {
        shadowCamera.update(camera.getPosition(), sunDirection);
    }

    public int shadowMapTexture() {
        return shadowBuffer.depthTexture();
    }

    /** See {@link ShadowCamera#depthWindowMin()}. */
    public float shadowDepthWindowMin() {
        return shadowCamera.depthWindowMin();
    }

    public float shadowDepthWindowMax() {
        return shadowCamera.depthWindowMax();
    }

    public Color fogColor() {
        return fogColor;
    }

    /**
     * Collects the frame's draw calls and runs the shadow, cloud, opaque and transparent passes,
     * leaving the scene in {@code target}. Binds {@code target} itself, after the shadow and cloud
     * passes have bound their own framebuffers.
     *
     * <p>Separate from {@link #drawHud} because the two land in different framebuffers. The scene
     * renders into the HDR buffer and is tonemapped afterwards; the HUD is authored in display
     * values and would dim whenever the player looked at the sun. Runs first in a frame, because
     * {@link #drawHud} draws from the call list collected here.
     */
    public void drawScene(Camera camera, GlFramebuffer target) {
        viewRotation.set(camera.getViewMatrix()).setTranslation(0, 0, 0);
        invViewRotation.set(viewRotation).transpose();
        invViewProj.set(camera.getProjectionMatrix()).mul(viewRotation).invert();
        updateLightMatrix(camera);

        setUniforms(camera);

        // Collect every renderable's calls first, then draw by pass. Gathering across all
        // renderables means transparent geometry blends against the full opaque scene, not
        // just whatever opaque calls happened to come before it in the same renderable.
        // NOTE: getDrawCalls must not set GL state — Renderer owns it entirely.
        calls.clear();
        for (IRenderable renderable : registered) calls.addAll(renderable.getDrawCalls(camera));
        for (DrawCall call : calls) EngineStats.countDrawCall(call.pass());

        // Guarded because this runs every frame — without the check we'd walk the call list three
        // extra times per frame just to build a message nobody is listening to.
        if (RENDER.isTraceEnabled()) {
            RENDER.trace(
                    "{} draw call(s): {} opaque, {} transparent, {} hud, {} shadow",
                    calls.size(),
                    countPass(calls, RenderPass.OPAQUE),
                    countPass(calls, RenderPass.TRANSPARENT),
                    countPass(calls, RenderPass.HUD),
                    countPass(calls, RenderPass.SHADOW));
        }

        // Timed from inside, one query per cascade — see the note on the TIMER_ constants for why
        // it cannot also be bracketed from out here.
        drawShadowPass(camera);

        // Before the scene, for the same reason the shadow pass is: it produces an input to it.
        // sky.frag samples what this writes. Order against the shadow pass does not matter — they
        // write to different buffers and neither reads the other — but both must be finished
        // before the target below is bound, because each one binds a framebuffer of its own.
        beginPass(TIMER_CLOUDS);
        if (features.clouds()) cloudRenderer.draw(cloudBuffer, frame, frameUniforms);
        else clearCloudBuffer();
        endPass(TIMER_CLOUDS);

        // Back to the scene target the shadow pass just took us away from. This is why drawScene
        // takes the target rather than Game binding it — only one of them can own the sequencing.
        //
        // The clear belongs here, immediately after the bind, because glClear acts on whatever
        // framebuffer is bound. Clearing before the shadow pass would clear the wrong one, and
        // skipping it leaves last frame's depth in place, which rejects every fragment from the
        // second frame onward.
        target.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Opaque pass: full depth test + write, no blending.
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);
        bindSharedTextures();
        beginBindTracking();
        beginPass(TIMER_OPAQUE);
        for (DrawCall call : calls) if (call.pass() == RenderPass.OPAQUE) submit(call, camera);
        endPass(TIMER_OPAQUE);

        // Transparent pass: depth test on so water is occluded by terrain, but depth write
        // off so transparent surfaces behind other transparent surfaces still draw.
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        bindSharedTextures();
        beginBindTracking();
        beginPass(TIMER_TRANSPARENT);
        for (DrawCall call : calls) if (call.pass() == RenderPass.TRANSPARENT) submit(call, camera);
        endPass(TIMER_TRANSPARENT);
    }

    /**
     * Fills the cloud buffer with "no clouds" on a frame that skips the cloud pass.
     *
     * <p>{@code sky.frag} composites it as {@code rgb * cloud.a + cloud.rgb}, so black at alpha 1
     * leaves the Preetham sky exactly as computed. {@link CloudRenderer} writes every pixel and
     * never clears, so skipping it leaves a fresh buffer at alpha 0, which multiplies the sky to
     * black.
     *
     * <p>The clear colour is global state and the scene clear reads it, so it is restored to the fog
     * colour before returning.
     */
    private void clearCloudBuffer() {
        cloudBuffer.bind();
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glClearColor(fogColor.red(), fogColor.green(), fogColor.blue(), fogColor.alpha());
    }

    /**
     * Renders the scene from the sun, depth only, into each cascade's layer. Runs before the opaque
     * pass because {@code chunk.frag} samples the result to decide what is shadowed. The shadow
     * framebuffer has no colour attachment, so the only state is depth and the only clear is the
     * depth bit.
     *
     * <p>A cascade whose matrix and world version both match the last render is skipped, keeping
     * its layer. See the note inside the loop.
     */
    private void drawShadowPass(Camera camera) {
        // Nothing downstream needs the map: setUniforms uploads every cascade split as 0 when
        // shadows are off, so cascadeFor() puts every fragment past the last split and chunk.frag
        // reads it as lit without a lookup. Returning here is what makes that free rather than
        // merely unused — the three cascades are the most expensive passes in the frame.
        if (!features.sunShadows()) return;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);

        // Front-face culling, only for this pass. Storing the depth of each caster's BACK faces
        // moves the recorded surface a whole block behind the lit one, so the front faces the
        // player sees are never within bias distance of their own stored depth — which is where
        // acne comes from. Works cleanly here because terrain is closed solid geometry; it would
        // break on single-sided planes, which this renderer does not produce.
        glCullFace(GL_FRONT);

        // No bindSharedTextures here, unlike the scene passes. shadow.vert and shadow.frag declare
        // no samplers, and unit 1 holds this very depth texture — binding it for reading while
        // rendering into one of its layers is a feedback loop. It was harmless before only because
        // nothing sampled it; not creating it is better than relying on that.
        beginBindTracking();

        /*
         * Once per cascade — but only if anything it depends on moved.
         *
         * A cascade's depth layer is a function of exactly two things: the matrix it is rendered
         * with, and the set of meshes in the world. Both are now quantised or counted, so "did it
         * change" is answerable without redrawing to find out. The matrix only steps when the eye
         * crosses one of that cascade's texels or the sun reaches its next quarter-degree, and the
         * far cascade's texels are half a block wide — so standing still, it is unchanged for
         * hundreds of frames and redrawing it is pure waste.
         *
         * That waste is not small. Measured, the 512-block cascade costs 18 ms a frame on its own,
         * dropping 75 fps to 32 — and because Game.processChunks uploads a fixed number of meshes
         * per FRAME, halving the frame rate also halves chunk streaming.
         *
         * Skipping means not clearing either: the layer keeps the contents it already had, which
         * are still correct precisely because nothing it depends on moved.
         */
        int worldVersion = worldVersion_.getAsInt();
        for (int cascade = 0; cascade < ShadowCamera.cascadeCount(); cascade++) {
            Matrix4f current = shadowCamera.lightViewProj(cascade);
            if (worldVersion == lastWorldVersion[cascade] && current.equals(lastCascadeMatrix[cascade])) continue;

            lastCascadeMatrix[cascade].set(current);
            lastWorldVersion[cascade] = worldVersion;

            // Below the `continue` on purpose: a cascade that skipped never opens a query, so it
            // reads back as -1 rather than 0. "Did no work" and "did its work instantly" have to
            // stay distinguishable, and standing still every cascade takes the first branch.
            beginPass(timerForCascade(cascade));

            // The clear has to sit inside the loop and after bindLayer, because it acts on
            // whichever layer is attached — clearing once outside would wipe one layer and leave
            // the others holding last frame's depth.
            shadowBuffer.bindLayer(cascade);
            glClear(GL_DEPTH_BUFFER_BIT);

            // shadow.vert takes one matrix under its own name rather than reading the array
            // chunk.frag uses — a vertex shader has no cascade to select, it IS the cascade.
            //
            // It cannot ride in frameUniforms any more. apply() uploads a program's frame
            // uniforms on the first draw that uses it and no-ops for the rest of the frame, so
            // cascades 1 and 2 would silently draw with cascade 0's matrix — casters projected from
            // the wrong place, on exactly the frames that redraw. Setting it straight on the program
            // holds because uniform values are program state: they survive the rebind submit() does
            // per call, so one upload per cascade covers every caster in it.
            boolean cascadeMatrixSet = false;
            for (DrawCall call : calls) {
                if (call.pass() != RenderPass.SHADOW || !call.castsInto(cascade)) continue;
                if (!cascadeMatrixSet) {
                    bindProgram(call.shader());
                    call.shader().setUniformMat4("uCascadeViewProj", current);
                    cascadeMatrixSet = true;
                }
                submit(call, camera);
            }
            endPass(timerForCascade(cascade));
        }
        glCullFace(GL_BACK);
    }

    /** Enables per-pass GPU timing. Left unset, which is the normal case, nothing is timed. */
    public void setGpuTimer(GpuTimer gpuTimer) {
        this.gpuTimer = gpuTimer;
    }

    public void setFrame(long frame) {
        this.frame = frame;
    }

    /**
     * Opens a GPU timer query for {@code pass}. Only one {@code GL_TIME_ELAPSED} query may be open
     * in the context, so every {@code beginPass} closes with its {@link #endPass} before the next
     * opens, and the timed regions are flat and sequential.
     */
    private void beginPass(int pass) {
        if (gpuTimer != null) gpuTimer.begin(pass, frame);
    }

    private void endPass(int pass) {
        if (gpuTimer != null) gpuTimer.end(pass);
    }

    /** Seconds since GLFW init, uploaded as {@code uTime}. */
    public void setTime(float time) {
        this.time = time;
    }

    /**
     * Draws the {@link RenderPass#HUD} calls collected by {@link #drawScene} over the window's
     * framebuffer: after post-processing, so untonemapped, with no depth test and alpha blending.
     * Restores depth test on and blending off afterwards.
     */
    public void drawHud(Camera camera) {
        // Drawn over everything, no depth test, blending on for alpha.
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        bindSharedTextures();
        beginBindTracking();
        for (DrawCall call : calls) if (call.pass() == RenderPass.HUD) submit(call, camera);

        // Restore sane defaults for the next frame.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void setUniforms(Camera camera) {
        frameUniforms.clear();
        frameUniforms.put("uTime", new UniformValue.F(time));
        frameUniforms.put("uView", new UniformValue.M4(camera.getViewMatrix()));
        frameUniforms.put("uProjection", new UniformValue.M4(camera.getProjectionMatrix()));
        frameUniforms.put("uInvViewProj", new UniformValue.M4(invViewProj));
        frameUniforms.put("uInvViewRotation", new UniformValue.M4(invViewRotation));
        frameUniforms.put("uSkyBrightness", new UniformValue.F(skyBrightness));
        frameUniforms.put("uExposure", new UniformValue.F(EXPOSURE));
        frameUniforms.put("uSunDirection", new UniformValue.V3(sunDirection));
        frameUniforms.put("uDayFactor", new UniformValue.F(sky.dayFactor()));
        frameUniforms.put("uNightHorizon", new UniformValue.V3(sky.nightHorizon()));
        frameUniforms.put("uNightZenith", new UniformValue.V3(sky.nightZenith()));
        frameUniforms.put("uA", new UniformValue.V3(coefficientA));
        frameUniforms.put("uB", new UniformValue.V3(coefficientB));
        frameUniforms.put("uC", new UniformValue.V3(coefficientC));
        frameUniforms.put("uD", new UniformValue.V3(coefficientD));
        frameUniforms.put("uE", new UniformValue.V3(coefficientE));
        frameUniforms.put("uZenith", new UniformValue.V3(skyZenith));
        frameUniforms.put("uZenithF", new UniformValue.V3(skyZenithF));
        // Zero extinction is how haze turns off. chunk.frag computes exp(-dist * uExtinction * d),
        // which is 1 for every fragment at zero, and its mix() then returns the lit colour
        // untouched — the same result as deleting the term, with no branch in the shader.
        frameUniforms.put("uExtinction", new UniformValue.F(features.distanceHaze() ? EXTINCTION : 0.0f));
        frameUniforms.put("uCameraPos", new UniformValue.V3(camera.getPosition()));
        frameUniforms.put("uCameraNear", new UniformValue.F(Camera.NEAR_PLANE));
        frameUniforms.put("uCameraFar", new UniformValue.F(Camera.FAR_PLANE));

        /*
         * One entry per cascade. glGetUniformLocation accepts an array element by name, so the
         * sealed UniformValue needs no array variant — "uLightViewProj[1]" is just a uniform.
         *
         * A split of 0 is how shadows turn off. chunk.frag's cascadeFor() walks the splits looking
         * for the first one the view distance is under; no distance is under 0, so it falls
         * through to CASCADE_COUNT, which the shader already treats as "outside every box, read as
         * lit". The other two uniforms still upload because a program that declares them is
         * entitled to a value, and nothing reads them once the cascade is out of range.
         */
        for (int cascade = 0; cascade < ShadowCamera.cascadeCount(); cascade++) {
            frameUniforms.put(
                    "uLightViewProj[" + cascade + "]", new UniformValue.M4(shadowCamera.lightViewProj(cascade)));
            frameUniforms.put("uShadowBias[" + cascade + "]", new UniformValue.F(shadowCamera.normalizedBias(cascade)));
            float split = features.sunShadows() ? ShadowCamera.splitDistance(cascade) : 0.0f;
            frameUniforms.put("uCascadeSplit[" + cascade + "]", new UniformValue.F(split));
        }
    }

    /**
     * Counts calls in one pass. Called only behind the {@code isTraceEnabled} guard, so the four
     * walks of the call list cost nothing unless the trace is on.
     */
    private static long countPass(List<DrawCall> calls, RenderPass pass) {
        return calls.stream().filter(c -> c.pass() == pass).count();
    }

    private void submit(DrawCall call, Camera camera) {
        // Optional.equals compares the contained atlas by identity, which is what is wanted: two
        // draws carrying the same atlas object need one bind, and null (start of a pass) matches
        // neither present nor empty, so the first call in a pass always binds.
        if (!call.atlas().equals(boundAtlas)) {
            if (call.atlas().isPresent()) call.atlas().get().bind();
            else glBindTexture(GL_TEXTURE_2D, 0);
            boundAtlas = call.atlas();
        }
        bindProgram(call.shader());

        // The only uniform that genuinely differs between two draws in the same frame. Everything
        // else the program declares is frame-constant and went up in bindProgram's apply.
        call.shader().setUniformMat4("uModel", call.transform());

        // Counted here rather than where the calls are built, because this is the point where a
        // mesh is actually submitted. The shadow pass walks the same call list once per cascade,
        // so a caster in two cascades lands here twice and is counted twice — which is the truth
        // about how much vertex work it caused.
        EngineStats.countVertices(call.pass(), call.mesh().vertexCount());
        call.mesh().render();
    }

    /**
     * Binds a program and does the per-program-per-frame work: its frame uniforms and its two
     * sampler units. A no-op when the program is already bound in this pass.
     *
     * <p>The samplers are set on every program change because a reload replaces the program, and
     * a rebuilt {@code chunk.frag} with its samplers left on unit 0 would sample the atlas as a
     * shadow map. {@code setUniformInt} is a no-op for a program that declares neither sampler.
     */
    private void bindProgram(ShaderProgram shader) {
        if (shader == boundShader) return;
        shader.bind();
        shader.setUniformInt("uShadowMap", SHADOW_TEXTURE_UNIT);
        shader.setUniformInt("uCloudBuffer", CLOUD_TEXTURE_UNIT);
        // Bind first, then upload: glUniform* writes into the active program. GlShader's own guard
        // makes this once per frame however many times it is reached.
        shader.apply(frame, frameUniforms);
        boundShader = shader;
    }

    /**
     * Binds the shadow map to unit 1 and the cloud buffer to unit 2 for the whole pass. They are
     * the same two objects all frame; binding them per draw cost 4,281 GL calls to reach state that
     * was already set.
     *
     * <p>Leaves unit 0 active, which {@code submit} relies on: {@code glActiveTexture} is global
     * state and the atlas bind goes to whichever unit is selected.
     */
    private void bindSharedTextures() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, shadowBuffer.depthTexture());
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, cloudBuffer.colorTexture());
        glActiveTexture(GL_TEXTURE0);
    }

    /** Forgets the tracked program and atlas, so the pass's first call binds both. */
    private void beginBindTracking() {
        boundShader = null;
        boundAtlas = null;
    }
}
