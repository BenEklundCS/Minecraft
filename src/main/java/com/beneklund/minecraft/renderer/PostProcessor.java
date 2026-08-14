package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.SkyMesh;

/*
 * The last passes of the frame. The scene arrives as a texture carrying linear radiance; this
 * extracts the bright parts, blurs them, adds them back, and tonemaps the result down to
 * something the display can show.
 *
 * Four passes, in order: bright -> horizontal blur -> vertical blur -> combine. The first three
 * run at half resolution because the output is a wide blur and its high frequencies are being
 * destroyed on purpose.
 */
public class PostProcessor {
    private static final String VERT_PATH = "/shaders/post.vert";
    private static final String FRAG_PATH = "/shaders/post.frag";
    private static final String BRIGHT_FRAG = "/shaders/bloom_bright.frag";
    private static final String BLUR_FRAG = "/shaders/bloom_blur.frag";

    // Scene radiance units, the same scale chunk.frag and sky.frag write in - not display units.
    // Measured at turbidity 2.5: a fully lit face reaches 15.4, and the sky peaks at 36 in the
    // few degrees around the sun, which is the number that matters - the Perez lobe, not the
    // zenith. The disc runs 75 (horizon) to 211 (overhead). 50 is the only gap, and a threshold
    // inside the sky's range blooms the glow instead of the sun, in a lopsided shape that
    // follows the lobe.
    private static final float BLOOM_THRESHOLD = 50.0f;
    private static final float BLOOM_STRENGTH = 0.6f;

    private final ShaderProgram shader;
    private final ShaderProgram brightShader;
    private final ShaderProgram blurShader;
    private final SkyMesh mesh;
    private final GlFramebuffer bloomA;
    private final GlFramebuffer bloomB;
    private final float exposure;

    public PostProcessor(float exposure, GlFramebuffer bloomA, GlFramebuffer bloomB) {
        this.exposure = exposure;
        this.bloomA = bloomA;
        this.bloomB = bloomB;
        shader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        brightShader = new ShaderProgram(VERT_PATH, BRIGHT_FRAG);
        blurShader = new ShaderProgram(VERT_PATH, BLUR_FRAG);
        mesh = new SkyMesh();
    }

    public void draw(int sceneTexture, int windowWidth, int windowHeight) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        // 1. Bright pass, scene -> bloomA. bind() sets the viewport to the target's own size,
        // so the half-resolution passes get a half-resolution viewport without asking.
        bloomA.bind();
        brightShader.bind();
        brightShader.setUniformInt("uScene", 0);
        brightShader.setUniformFloat("uBloomThreshold", BLOOM_THRESHOLD);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        mesh.render();

        // 2 and 3. Ping-pong, because a framebuffer cannot sample the texture it is rendering
        // into - that's undefined, and it fails as driver-dependent garbage rather than an error.
        blurPass(bloomB, bloomA.colorTexture(), 1.0f, 0.0f);
        blurPass(bloomA, bloomB.colorTexture(), 0.0f, 1.0f);

        // 4. Combine and tonemap, into the window.
        GlFramebuffer.bindDefault(windowWidth, windowHeight);
        shader.bind();
        shader.setUniformInt("uScene", 0);
        shader.setUniformInt("uBloom", 1);
        shader.setUniformFloat("uExposure", exposure);
        shader.setUniformFloat("uBloomStrength", BLOOM_STRENGTH);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomA.colorTexture());
        // Back to unit 0 before leaving: the active unit is global, and the next glBindTexture
        // anywhere - the atlas, next frame - would otherwise land on unit 1.
        glActiveTexture(GL_TEXTURE0);
        mesh.render();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
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

    public void reload() {
        shader.reload();
        brightShader.reload();
        blurShader.reload();
    }

    public void delete() {
        shader.delete();
        brightShader.delete();
        blurShader.delete();
        mesh.delete();
    }
}
