package com.beneklund.minecraft.renderer;

import static com.beneklund.minecraft.util.Log.RENDER;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.ShadowFramebuffer;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.world.PreethamSky;
import com.beneklund.minecraft.world.SkyModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Collects DrawCalls from all Renderables each frame and submits them to the GPU,
// opaque pass first then transparent pass (see draw()).
public class Renderer {
    private final List<IRenderable> registered;
    private Color fogColor;
    private Vector3f sunDirection;
    private float skyBrightness;

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
    private final Matrix4f modelScratch = new Matrix4f();
    // Unit 0 is the block atlas, bound by submit() from the DrawCall. The shadow map needs a unit
    // of its own or the depth lookup samples the atlas instead — samplers cannot ride in
    // frameUniforms, because UniformValue is sealed over float/vec3/mat4 with no int variant.
    private static final int SHADOW_TEXTURE_UNIT = 1;

    private final Map<String, UniformValue<?>> frameUniforms = new HashMap<>();

    // Collected by drawScene, filtered again by drawHud. Render-thread-only, like everything here.
    private final List<DrawCall> calls = new ArrayList<>();

    private final ShadowFramebuffer shadowBuffer;

    // The sun's projection. Everything about where the shadow map is rendered from lives there,
    // deliberately out of reach of the window and the view matrix — see the note on the class.
    private final ShadowCamera shadowCamera;

    public Renderer(
            List<IRenderable> registered, Color fogColor, ShadowFramebuffer shadowBuffer, ShadowCamera shadowCamera) {
        this.registered = registered;
        this.fogColor = fogColor;
        this.shadowBuffer = shadowBuffer;
        this.shadowCamera = shadowCamera;
        // Seeded so the sky uniforms are never null if a frame draws before Game.run pushes
        // the real sun position. The sky shader resolves them to a real location, so unlike
        // the chunk shader it would NPE rather than no-op.
        setSunDirection(new Vector3f(0.0f, 1.0f, 0.0f));
    }

    public void delete() {
        RENDER.debug("deleting {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.delete();
        shadowBuffer.delete();
    }

    public void reloadAll() {
        RENDER.info("reloading {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.reload();
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

    /*
     * Where the sun looks from. The maths lives in ShadowCamera, which takes a POSITION and a sun
     * direction and has no way to see where the player is looking — shadows moving with the mouse
     * is the bug that separation exists to make unexpressible.
     */
    private void updateLightMatrix(Camera camera) {
        shadowCamera.update(camera.getPosition(), sunDirection);
    }

    public int shadowMapTexture() {
        return shadowBuffer.depthTexture();
    }

    /*
     * The slice of the shadow map's [0,1] depth range that terrain actually occupies, for the
     * debug overlay to stretch across its contrast range.
     *
     * The light's eye sits SUN_DISTANCE in front of the box centre, so the centre lands at that
     * depth; terrain reaches roughly a world-height either side of it. Everything outside this
     * window is either empty sky or below bedrock.
     */
    public float shadowDepthWindowMin() {
        return shadowCamera.depthWindowMin();
    }

    public float shadowDepthWindowMax() {
        return shadowCamera.depthWindowMax();
    }

    public Color fogColor() {
        return fogColor;
    }

    /*
     * Split from drawHud because the two land in different framebuffers. The scene renders into
     * the HDR buffer and gets tonemapped on the way out; the HUD is authored in display values
     * and must not be, or the crosshair dims whenever the player looks at the sun.
     *
     * drawScene has to run first in a frame - drawHud filters the call list this collected.
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

        drawShadowPass(camera);

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
        for (DrawCall call : calls) if (call.pass() == RenderPass.OPAQUE) submit(call, camera);

        // Transparent pass: depth test on so water is occluded by terrain, but depth write
        // off so transparent surfaces behind other transparent surfaces still draw.
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (DrawCall call : calls) if (call.pass() == RenderPass.TRANSPARENT) submit(call, camera);
    }

    /*
     * The scene from the sun's point of view, depth only. Runs before the opaque pass because it
     * produces an input to it — chunk.frag samples this map to decide what is shadowed.
     *
     * No colour state to set: ShadowFramebuffer has no colour attachment, so there is nothing to
     * clear or blend and glClear takes the depth bit alone.
     */
    private void drawShadowPass(Camera camera) {
        shadowBuffer.bind();
        glClear(GL_DEPTH_BUFFER_BIT);
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
        for (DrawCall call : calls) if (call.pass() == RenderPass.SHADOW) submit(call, camera);
        glCullFace(GL_BACK);
    }

    // Runs after the post pass, against the default framebuffer, so nothing here is tonemapped.
    public void drawHud(Camera camera) {
        // Drawn over everything, no depth test, blending on for alpha.
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (DrawCall call : calls) if (call.pass() == RenderPass.HUD) submit(call, camera);

        // Restore sane defaults for the next frame.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void setUniforms(Camera camera) {
        frameUniforms.clear();
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
        frameUniforms.put("uModel", new UniformValue.M4(modelScratch));
        frameUniforms.put("uExtinction", new UniformValue.F(EXTINCTION));
        frameUniforms.put("uShadowBias", new UniformValue.F(shadowCamera.normalizedBias()));
        frameUniforms.put("uCameraPos", new UniformValue.V3(camera.getPosition()));
        frameUniforms.put("uLightViewProj", new UniformValue.M4(shadowCamera.lightViewProj()));
    }

    private static long countPass(List<DrawCall> calls, RenderPass pass) {
        return calls.stream().filter(c -> c.pass() == pass).count();
    }

    private void submit(DrawCall call, Camera camera) {
        if (call.atlas().isPresent()) call.atlas().get().bind();
        else glBindTexture(GL_TEXTURE_2D, 0);
        modelScratch.set(call.transform());
        call.shader().bind();

        // Bound per call because the shader changes per call and the sampler uniform lives on the
        // program. setUniformInt is a no-op for shaders that do not declare it — GlShader.location
        // returns -1 and the setter bails — so this costs one glUniform1i on chunk.frag alone.
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowBuffer.depthTexture());
        glActiveTexture(GL_TEXTURE0);
        call.shader().setUniformInt("uShadowMap", SHADOW_TEXTURE_UNIT);

        call.shader().apply(frameUniforms, call.uniforms());
        call.mesh().render();
    }
}
