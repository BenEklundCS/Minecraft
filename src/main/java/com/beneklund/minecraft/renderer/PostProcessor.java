package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.SkyMesh;
import java.util.Optional;
import org.joml.Vector2f;

/*
 * The last passes of the frame. The scene arrives as a texture carrying linear radiance; this
 * extracts the bright parts, blurs them, adds them back, and tonemaps the result down to
 * something the display can show.
 *
 * Six passes, in order: occlusion -> radial blur -> bright -> horizontal blur -> vertical blur ->
 * combine. Everything before the combine runs at half resolution, because every one of those
 * outputs is a wide blur whose high frequencies are being destroyed on purpose.
 *
 * The first two are the light shafts and are skipped on any frame where the sun is behind the
 * camera; the middle three are bloom. draw() is the map — each step is one named call, and the
 * ordering constraints between them live on the methods themselves.
 */
public class PostProcessor {
    private static final String VERT_PATH = "/shaders/post.vert";
    private static final String FRAG_PATH = "/shaders/post.frag";
    private static final String BRIGHT_FRAG = "/shaders/bloom_bright.frag";
    private static final String BLUR_FRAG = "/shaders/bloom_blur.frag";
    private static final String DEBUG_DEPTH_FRAG = "/shaders/debug_depth.frag";

    // Fraction of the window width the shadow-map inset occupies. A quarter is large enough to
    // read texel structure and small enough to leave the world visible beside it, which is the
    // point — the map has to be watched while moving, not in isolation.
    private static final float DEBUG_INSET_FRACTION = 0.25f;

    private static final float GODRAY_DECAY = 0.95f;
    private static final float GODRAY_WEIGHT = 0.052f; // (1 - DECAY) / (1 - DECAY^64)
    private static final float GODRAY_DENSITY = 0.6f;
    private static final float GODRAY_STRENGTH = 0.25f;

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

    // Display-only. The occlusion buffer holds linear radiance, and the sun disc reaches
    // SUN_INTENSITY = 270 (sky.frag:17); dividing by it puts the disc at 1.0 so the tonemap has
    // something in range to work on. Not used by the effect — only by the 2a checkpoint.
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

    public PostProcessor(
            float exposure, GlFramebuffer bloomA, GlFramebuffer bloomB, GlFramebuffer godrayA, GlFramebuffer godrayB) {
        this.exposure = exposure;
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

    /*
     * Draws the shadow map into a square inset in the bottom-right corner, over the finished
     * frame. Runs against the default framebuffer, after draw(), so it is never tonemapped —
     * this is an instrument, not part of the image.
     *
     * depthMin/depthMax select which slice of the depth range is stretched across the contrast
     * range; the caller knows the light's near/far and where the terrain sits within them.
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

    public void draw(
            int sceneTexture, int sceneDepthTexture, Optional<Vector2f> sunUV, int windowWidth, int windowHeight) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        // 0. Godrays, scene+depth -> godrayA -> godrayB. Skipped entirely when the sun is behind
        // the camera: there is no screen position to radiate from, and marching toward a phantom
        // one puts shafts around nothing.
        boolean godrays = sunUV.isPresent();
        if (godrays) {
            occlusionPass(sceneTexture, sceneDepthTexture);
            godrayPass(sunUV.get());
        }

        // 1. Bright pass, scene -> bloomA.
        brightPass(sceneTexture);

        // 2 and 3. Ping-pong, because a framebuffer cannot sample the texture it is rendering
        // into - that's undefined, and it fails as driver-dependent garbage rather than an error.
        blurPass(bloomB, bloomA.colorTexture(), 1.0f, 0.0f);
        blurPass(bloomA, bloomB.colorTexture(), 0.0f, 1.0f);

        // 4. Combine and tonemap, into the window.
        compositePass(sceneTexture, godrays, windowWidth, windowHeight);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    // Everything over BLOOM_THRESHOLD, scene -> bloomA. bind() sets the viewport to the target's
    // own size, so the half-resolution passes get a half-resolution viewport without asking.
    private void brightPass(int sceneTexture) {
        bloomA.bind();
        brightShader.bind();
        brightShader.setUniformInt("uScene", 0);
        brightShader.setUniformFloat("uBloomThreshold", BLOOM_THRESHOLD);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        mesh.render();
    }

    /*
     * The only pass that writes to the window rather than to a framebuffer, and the only one that
     * tonemaps. Everything upstream is linear radiance; what leaves here is display values.
     *
     * Three inputs on three units: the scene, the blurred bright parts, and the light shafts. The
     * godray bind is unconditional even on a frame that produced no shafts — a sampler pointing at
     * a unit with nothing bound is undefined, so the stale buffer stays bound and godrayStrength
     * multiplies it out instead.
     */
    private void compositePass(int sceneTexture, boolean godrays, int windowWidth, int windowHeight) {
        GlFramebuffer.bindDefault(windowWidth, windowHeight);
        shader.bind();
        shader.setUniformInt("uScene", 0);
        shader.setUniformInt("uBloom", 1);
        shader.setUniformInt("uGodray", 2);
        shader.setUniformFloat("uGodrayStrength", godrays ? GODRAY_STRENGTH : 0.0f);
        shader.setUniformFloat("uExposure", exposure);
        shader.setUniformFloat("uBloomStrength", BLOOM_STRENGTH);
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

    /*
     * Two texture units, because this is the first pass that needs to read the scene twice over:
     * the colour it might keep, and the depth that decides whether to keep it.
     *
     * Leaves the active unit back on GL_TEXTURE0. That is global state, and the bright pass runs
     * next against unit 0 — a stale unit 1 here would land the scene bind on the wrong unit and
     * bloom would come out of the depth texture.
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
