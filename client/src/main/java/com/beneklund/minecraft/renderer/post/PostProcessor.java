package com.beneklund.minecraft.renderer.post;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.SkyMesh;
import com.beneklund.minecraft.renderer.IGpuResource;
import com.beneklund.minecraft.renderer.RenderFeatures;
import com.beneklund.minecraft.renderer.asset.ShaderProgram;
import java.util.Optional;
import org.joml.Vector2f;

/**
 * Runs the frame's last passes: light shafts, bloom, and the tonemap from linear HDR radiance to
 * display values.
 *
 * <p>{@link #draw} runs up to six fullscreen passes, each one named call:
 *
 * <ol>
 *   <li>occlusion: sky pixels only, scene colour and depth into {@code godrayA}
 *   <li>radial blur toward the sun: {@code godrayA} into {@code godrayB}
 *   <li>bright pass: radiance above {@link #BLOOM_THRESHOLD} into {@code bloomA}
 *   <li>horizontal Gaussian: {@code bloomA} into {@code bloomB}
 *   <li>vertical Gaussian: {@code bloomB} into {@code bloomA}
 *   <li>composite and ACES tonemap: scene, {@code bloomA} and {@code godrayB} into the window
 * </ol>
 *
 * <p>Passes 1 to 5 run at half resolution because each output is a wide blur. The light shafts
 * skip any frame where the sun is behind the camera. {@link RenderFeatures} can turn off either
 * group for the whole run. The composite always runs, because it is the only tonemap.
 *
 * @see <a href="https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process">
 *     GPU Gems 3, ch. 13: Volumetric Light Scattering as a Post-Process</a>
 * @see <a href="https://learnopengl.com/Advanced-Lighting/Bloom">LearnOpenGL: Bloom</a>
 * @see <a href="https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/">
 *     Narkowicz: ACES Filmic Tone Mapping Curve</a>
 */
public class PostProcessor implements IGpuResource {
    private static final String VERT_PATH = "/shaders/post.vert";
    private static final String FRAG_PATH = "/shaders/post.frag";
    private static final String BRIGHT_FRAG = "/shaders/bloom_bright.frag";
    private static final String BLUR_FRAG = "/shaders/bloom_blur.frag";
    private static final String DEBUG_DEPTH_FRAG = "/shaders/debug_depth.frag";

    /**
     * Fraction of the window width the shadow-map inset occupies. A quarter shows texel structure
     * and leaves the world visible beside it, so the map can be watched while moving.
     */
    private static final float DEBUG_INSET_FRACTION = 0.25f;

    private static final float GODRAY_DECAY = 0.95f;
    private static final float GODRAY_WEIGHT = 0.052f; // (1 - DECAY) / (1 - DECAY^64)
    private static final float GODRAY_DENSITY = 0.6f;
    private static final float GODRAY_STRENGTH = 0.6f;

    // Scene radiance units, the same scale chunk.frag and sky.frag write in - not display units.
    // Measured at turbidity 2.5: a fully lit face reaches 15.4, and the sky peaks at 36 in the
    // few degrees around the sun, which is the number that matters - the Perez lobe, not the
    // zenith. The disc runs 75 (horizon) to 211 (overhead). 50 is the only gap, and a threshold
    // inside the sky's range blooms the glow instead of the sun, in a lopsided shape that
    // follows the lobe.
    private static final float BLOOM_THRESHOLD = 50.0f;
    private static final float BLOOM_STRENGTH = 0.6f;

    private static final String GODRAY_OCCLUSION_FRAG = "/shaders/godray_occlusion.frag";
    private static final String GODRAY_BLUR_FRAG = "/shaders/godray_blur.frag";

    /**
     * Scales the occlusion buffer for viewing it directly: the sun disc reaches {@code
     * SUN_INTENSITY = 270} in {@code sky.frag}, and dividing by it puts the disc at 1.0. Nothing
     * reads this constant.
     */
    private static final float GODRAY_DISPLAY_SCALE = 1.0f / 270.0f;

    private final ShaderProgram occlusionShader;
    private final ShaderProgram godrayShader;

    private final ShaderProgram shader;
    private final ShaderProgram brightShader;
    private final ShaderProgram blurShader;
    private final ShaderProgram debugDepthShader;
    private final SkyMesh mesh;
    private final GlFramebuffer bloomA;
    private final GlFramebuffer bloomB;
    private final GlFramebuffer godrayA;
    private final GlFramebuffer godrayB;
    private final float exposure;

    /** Switches bloom and the light shafts on or off for the run. The tonemap always runs. */
    private final RenderFeatures features;

    public PostProcessor(
            float exposure,
            GlFramebuffer bloomA,
            GlFramebuffer bloomB,
            GlFramebuffer godrayA,
            GlFramebuffer godrayB,
            RenderFeatures features) {
        this.exposure = exposure;
        this.features = features;
        this.bloomA = bloomA;
        this.bloomB = bloomB;
        this.godrayA = godrayA;
        this.godrayB = godrayB;

        shader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        occlusionShader = new ShaderProgram(VERT_PATH, GODRAY_OCCLUSION_FRAG);
        godrayShader = new ShaderProgram(VERT_PATH, GODRAY_BLUR_FRAG);
        brightShader = new ShaderProgram(VERT_PATH, BRIGHT_FRAG);
        blurShader = new ShaderProgram(VERT_PATH, BLUR_FRAG);
        debugDepthShader = new ShaderProgram(VERT_PATH, DEBUG_DEPTH_FRAG);
        mesh = new SkyMesh();
    }

    /**
     * Draws one shadow-map layer into a square inset in the bottom-right corner of the window.
     * Call it after {@link #draw}; it writes to the default framebuffer and skips the tonemap,
     * so it shows raw depth.
     *
     * @param depthMin start of the depth slice stretched across the inset's contrast range
     * @param depthMax end of that slice; the caller picks both from where the terrain sits between
     *     the light's near and far planes
     */
    public void drawDepthOverlay(
            int depthTexture, int layer, float depthMin, float depthMax, int windowWidth, int windowHeight) {
        int size = (int) (windowWidth * DEBUG_INSET_FRACTION);
        GlFramebuffer.bindDefault(windowWidth, windowHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        // The inset is drawn by shrinking the viewport, not by changing the geometry: the same
        // fullscreen triangle covers whatever rectangle the viewport describes.
        glViewport(windowWidth - size, 0, size, size);
        debugDepthShader.bind();
        debugDepthShader.setUniformInt("uDepth", 0);
        debugDepthShader.setUniformFloat("uLayer", layer);
        debugDepthShader.setUniformFloat("uDepthMin", depthMin);
        debugDepthShader.setUniformFloat("uDepthMax", depthMax);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTexture);
        mesh.render();

        glViewport(0, 0, windowWidth, windowHeight);
    }

    /**
     * Runs every enabled pass and leaves the tonemapped frame in the window. Restores depth
     * testing and face culling on the way out.
     *
     * @param sceneTexture the scene's linear HDR colour
     * @param sceneDepthTexture the scene's depth, read by the occlusion pass
     * @param sunUV the sun's screen position in [0, 1] UV, empty when it is behind the camera
     */
    public void draw(
            int sceneTexture, int sceneDepthTexture, Optional<Vector2f> sunUV, int windowWidth, int windowHeight) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        // 0. Godrays, scene+depth -> godrayA -> godrayB. Skipped entirely when the sun is behind
        // the camera: there is no screen position to radiate from, and marching toward a phantom
        // one puts shafts around nothing.
        boolean godrays = sunUV.isPresent() && features.godrays();
        if (godrays) {
            occlusionPass(sceneTexture, sceneDepthTexture);
            godrayPass(sunUV.get());
        }

        // 1, 2 and 3. Bright pass into bloomA, then a ping-pong blur - a framebuffer cannot sample
        // the texture it is rendering into, and that fails as driver-dependent garbage rather than
        // an error. Skipped together: the composite reads bloomA either way, and a stale buffer
        // multiplied by a zero strength is the same pattern the sun-behind-camera case already uses.
        boolean bloom = features.bloom();
        if (bloom) {
            brightPass(sceneTexture);
            blurPass(bloomB, bloomA.colorTexture(), 1.0f, 0.0f);
            blurPass(bloomA, bloomB.colorTexture(), 0.0f, 1.0f);
        }

        // 4. Combine and tonemap, into the window.
        compositePass(sceneTexture, godrays, bloom, windowWidth, windowHeight);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    /**
     * Keeps radiance above {@link #BLOOM_THRESHOLD}, scene into {@code bloomA}. {@link
     * GlFramebuffer#bind()} sets the half-resolution viewport.
     */
    private void brightPass(int sceneTexture) {
        bloomA.bind();
        brightShader.bind();
        brightShader.setUniformInt("uScene", 0);
        brightShader.setUniformFloat("uBloomThreshold", BLOOM_THRESHOLD);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        mesh.render();
    }

    /**
     * Adds bloom and light shafts to the scene, applies exposure and the ACES tonemap, and writes
     * display values to the window. The only pass that targets the window during {@link #draw}.
     *
     * <p>Binds the scene, {@code bloomA} and {@code godrayB} to units 0, 1 and 2 on every frame,
     * including frames that skipped bloom or the light shafts. A sampler on a unit with nothing
     * bound is undefined, so a skipped group keeps its stale buffer bound and a strength of zero
     * multiplies it out.
     */
    private void compositePass(int sceneTexture, boolean godrays, boolean bloom, int windowWidth, int windowHeight) {
        GlFramebuffer.bindDefault(windowWidth, windowHeight);
        shader.bind();
        shader.setUniformInt("uScene", 0);
        shader.setUniformInt("uBloom", 1);
        shader.setUniformInt("uGodray", 2);
        shader.setUniformFloat("uGodrayStrength", godrays ? GODRAY_STRENGTH : 0.0f);
        shader.setUniformFloat("uExposure", exposure);
        shader.setUniformFloat("uBloomStrength", bloom ? BLOOM_STRENGTH : 0.0f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomA.colorTexture());
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, godrayB.colorTexture());
        // Back to unit 0 before leaving: the active unit is global, and the next glBindTexture
        // anywhere - the atlas, next frame - would otherwise land on unit 2.
        glActiveTexture(GL_TEXTURE0);
        mesh.render();
    }

    /**
     * Writes sky pixels' radiance into {@code godrayA} and blacks out everything else, so the
     * radial blur smears only the sky and gaps behind silhouettes become shafts. Sky is where depth
     * equals the clear value 1.0; colour is on unit 0, depth on unit 1.
     *
     * <p>Leaves {@code GL_TEXTURE0} active. The active unit is global state, and the bright pass
     * binds the scene next assuming unit 0; left on unit 1, bloom would read the depth texture.
     */
    private void occlusionPass(int sceneTexture, int sceneDepthTexture) {
        godrayA.bind();
        occlusionShader.bind();
        occlusionShader.setUniformInt("uScene", 0);
        occlusionShader.setUniformInt("uSceneDepth", 1);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneDepthTexture);
        glActiveTexture(GL_TEXTURE0);
        mesh.render();
    }

    /**
     * One direction of the separable Gaussian, {@code sourceTexture} into {@code target}. A
     * framebuffer can't sample its own colour texture, so the two directions ping-pong between
     * {@code bloomA} and {@code bloomB}.
     */
    private void blurPass(GlFramebuffer target, int sourceTexture, float dx, float dy) {
        target.bind();
        blurShader.bind();
        blurShader.setUniformInt("uSource", 0);
        blurShader.setUniformVec2("uTexelSize", 1.0f / target.width(), 1.0f / target.height());
        blurShader.setUniformVec2("uDirection", dx, dy);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        mesh.render();
    }

    /**
     * Blurs {@code godrayA} radially toward {@code sunUV} into {@code godrayB}, summing decaying
     * samples along each pixel's line to the sun.
     */
    private void godrayPass(Vector2f sunUV) {
        godrayB.bind();
        godrayShader.bind();
        godrayShader.setUniformVec2("uSunUV", sunUV.x, sunUV.y);
        godrayShader.setUniformFloat("uDensity", GODRAY_DENSITY);
        godrayShader.setUniformFloat("uWeight", GODRAY_WEIGHT);
        godrayShader.setUniformFloat("uDecay", GODRAY_DECAY);
        godrayShader.setUniformInt("uOcclusion", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, godrayA.colorTexture());
        mesh.render();
    }

    public void reload() {
        shader.reload();
        debugDepthShader.reload();
        brightShader.reload();
        blurShader.reload();
        occlusionShader.reload();
        godrayShader.reload();
    }

    public void delete() {
        shader.delete();
        debugDepthShader.delete();
        brightShader.delete();
        blurShader.delete();
        occlusionShader.delete();
        godrayShader.delete();
        mesh.delete();
    }
}
