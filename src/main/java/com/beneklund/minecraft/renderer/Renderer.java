package com.beneklund.minecraft.renderer;

import java.util.List;

// collects DrawCalls from all Renderables each frame and submits them to the GPU.
// Replaces ChunkRenderer once the full draw-call pipeline is in place.
public class Renderer {
    private final List<IRenderable> registered;

    public Renderer(List<IRenderable> registered) {
        this.registered = registered;
    }

    public void delete() {
        for (IRenderable r : registered) r.delete();
    }

    public void draw(Camera camera) {
        for (IRenderable renderable : registered) {
            List<DrawCall> drawCalls = renderable.getDrawCalls(camera);
            for (DrawCall drawCall : drawCalls) {
                drawCall.shader().bind();
                drawCall.shader().setUniformMat4("uView", camera.getViewMatrix());
                drawCall.shader().setUniformMat4("uProjection", camera.getProjectionMatrix());
                drawCall.shader().setUniformMat4("uModel", drawCall.transform());
                drawCall.mesh().render();
            }
        }
    }
}
