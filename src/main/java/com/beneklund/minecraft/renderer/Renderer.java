package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

// Collects DrawCalls from all Renderables each frame and submits them to the GPU,
// opaque pass first then transparent pass (see draw()).
public class Renderer {
    private final List<IRenderable> registered;

    public Renderer(List<IRenderable> registered) {
        this.registered = registered;
    }

    public void delete() {
        for (IRenderable r : registered) r.delete();
    }

    public void draw(Camera camera) {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        // Collect every renderable's calls first, then draw by pass. Gathering across all
        // renderables means transparent geometry blends against the full opaque scene, not
        // just whatever opaque calls happened to come before it in the same renderable.
        List<DrawCall> calls = new ArrayList<>();
        for (IRenderable renderable : registered) calls.addAll(renderable.getDrawCalls(camera));

        // Opaque pass: write depth normally, no blending.
        glDepthMask(true);
        glDisable(GL_BLEND);
        for (DrawCall call : calls) if (call.pass() == RenderPass.OPAQUE) submit(call, camera);

        // Transparent pass: blend over the opaque scene. Depth test stays on (so water is
        // occluded by terrain in front of it) but depth writes are off (so transparent
        // surfaces behind other transparent surfaces still draw instead of self-occluding).
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (DrawCall call : calls) if (call.pass() == RenderPass.TRANSPARENT) submit(call, camera);

        // Restore the default for the next frame's opaque work.
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void submit(DrawCall call, Camera camera) {
        call.shader().bind();
        call.shader().setUniformMat4("uView", camera.getViewMatrix());
        call.shader().setUniformMat4("uProjection", camera.getProjectionMatrix());
        call.shader().setUniformMat4("uModel", call.transform());
        call.mesh().render();
    }
}
