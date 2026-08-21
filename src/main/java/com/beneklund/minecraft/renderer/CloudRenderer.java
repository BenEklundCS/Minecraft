package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glDisable;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.SkyMesh;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import java.util.Map;

/*
 * The cloud volume, raymarched into a buffer of its own before the scene is drawn. sky.frag then
 * samples that buffer and composites the result, so the sky pass is now two passes.
 *
 * Not an IRenderable, and the difference is the point: a DrawCall cannot choose a render target,
 * and this one has to, because the march is far too expensive at full resolution. Same shape as
 * the shadow pass — render into a buffer first, hand it to a later shader as a texture — rather
 * than the shape of a PostProcessor step, because the clouds are part of the image the tonemap
 * later operates on and not something applied to a finished one.
 *
 * Shares sky.vert with SkyRenderer: both want a world-space view ray per pixel off the same
 * fullscreen triangle, and that is the whole of the vertex stage's job here.
 */
public class CloudRenderer {
    private static final String VERT_PATH = "/shaders/sky.vert";
    private static final String FRAG_PATH = "/shaders/cloud.frag";

    private final ShaderProgram shader;
    private final SkyMesh mesh;

    public CloudRenderer() {
        shader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        mesh = new SkyMesh();
    }

    /*
     * No glClear. The triangle covers the target and cloud.frag writes every pixel it reaches,
     * including the ones with no cloud in them — clearing first would be a second full-target write
     * per frame for a result that is overwritten immediately.
     *
     * Depth off for the same reason it is off in PostProcessor: one screen-covering triangle has
     * nothing to sort against. The clouds end up behind terrain anyway, because the sky pass that
     * reads this buffer draws before any of it.
     */
    public void draw(GlFramebuffer target, Map<String, UniformValue<?>> frameUniforms) {
        target.bind();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        shader.bind();
        shader.apply(frameUniforms, Map.of());
        mesh.render();
    }

    public void reload() {
        shader.reload();
    }

    public void delete() {
        shader.delete();
        mesh.delete();
    }
}
