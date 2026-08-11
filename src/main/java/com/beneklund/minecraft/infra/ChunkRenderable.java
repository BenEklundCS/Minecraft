package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.renderer.*;
import java.util.ArrayList;
import java.util.List;

public class ChunkRenderable implements IRenderable {
    private static final String VERT_PATH = "/shaders/chunk.vert";
    private static final String FRAG_PATH = "/shaders/chunk.frag";

    private final RenderWorld renderWorld;
    private final TextureAtlas atlas;
    // Instance, not static final: a static initializer runs at class-load, which isn't
    // guaranteed to be after the GL context exists.
    private final ShaderProgram chunkShader;

    public ChunkRenderable(RenderWorld renderWorld, TextureAtlas atlas) {
        this.renderWorld = renderWorld;
        this.atlas = atlas;
        // Constructing directly rather than calling reload() — a shader that won't compile at
        // startup should stop the game with the GLSL error, and reload() has nothing to fall
        // back to before this assignment anyway.
        chunkShader = new ShaderProgram(VERT_PATH, FRAG_PATH);
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        Frustum frustum = new Frustum(camera.getViewProjectionMatrix());
        List<DrawCall> result = new ArrayList<>();
        for (RenderWorld.Entry entry : renderWorld.getEntries()) {
            if (!frustum.isVisible(entry.bounds())) continue;
            if (entry.opaqueMesh() != null) {
                result.add(new DrawCall(entry.opaqueMesh(), entry.model(), chunkShader, atlas, RenderPass.OPAQUE));
            }
            if (entry.transparentMesh() != null) {
                result.add(new DrawCall(
                        entry.transparentMesh(), entry.model(), chunkShader, atlas, RenderPass.TRANSPARENT));
            }
        }
        return result;
    }

    @Override
    public void reload() {
        chunkShader.reload();
    }

    @Override
    public void delete() {
        chunkShader.delete();
    }
}
