package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.util.Color;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector2f;

// Collects DrawCalls from all Renderables each frame and submits them to the GPU,
// opaque pass first then transparent pass (see draw()).
public class Renderer {
    private final List<IRenderable> registered;
    private final Color fogColor;
    private final Vector2f fogRange;

    public Renderer(List<IRenderable> registered, Color fogColor, Vector2f fogRange) {
        this.registered = registered;
        this.fogColor = fogColor;
        this.fogRange = fogRange;
    }

    public void delete() {
        for (IRenderable r : registered) r.delete();
    }

    public void reloadAll() {
        for (IRenderable r : registered) r.reload();
    }

    public void draw(Camera camera) {
        // Collect every renderable's calls first, then draw by pass. Gathering across all
        // renderables means transparent geometry blends against the full opaque scene, not
        // just whatever opaque calls happened to come before it in the same renderable.
        // NOTE: getDrawCalls must not set GL state — Renderer owns it entirely.
        List<DrawCall> calls = new ArrayList<>();
        for (IRenderable renderable : registered) calls.addAll(renderable.getDrawCalls(camera));

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

        // HUD pass: drawn last over everything, no depth test, blending on for alpha.
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

    private void submit(DrawCall call, Camera camera) {
        if (call.atlas().isPresent()) call.atlas().get().bind();
        else glBindTexture(GL_TEXTURE_2D, 0);
        call.shader().bind();
        call.shader().setUniformMat4("uView", camera.getViewMatrix());
        call.shader().setUniformMat4("uProjection", camera.getProjectionMatrix());
        call.shader().setUniformMat4("uModel", call.transform());
        call.shader().setUniformVec3("uFogColor", fogColor.toRgbVec3());
        call.shader().setUniformFloat("uFogStart", fogRange.x);
        call.shader().setUniformFloat("uFogEnd", fogRange.y);
        call.mesh().render();
    }
}
