package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.DrawCall;
import com.beneklund.minecraft.renderer.Frustum;
import com.beneklund.minecraft.renderer.IRenderable;
import com.beneklund.minecraft.renderer.ShaderProgram;
import com.beneklund.minecraft.renderer.TextureAtlas;
import java.util.ArrayList;
import java.util.List;

public class ChunkRenderable implements IRenderable {
    private final RenderWorld renderWorld;
    private final TextureAtlas atlas;
    private static final ShaderProgram CHUNK_SHADER = new ShaderProgram("/shaders/chunk.vert", "/shaders/chunk.frag");

    public ChunkRenderable(RenderWorld renderWorld, TextureAtlas atlas) {
        this.renderWorld = renderWorld;
        this.atlas = atlas;
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        atlas.bind();
        Frustum frustum = new Frustum(camera.getViewProjectionMatrix());
        List<DrawCall> result = new ArrayList<>();
        for (RenderWorld.Entry entry : renderWorld.getEntries()) {
            if (!frustum.isVisible(entry.bounds())) continue;
            result.add(new DrawCall(entry.mesh(), entry.model(), CHUNK_SHADER));
        }
        return result;
    }

    @Override
    public void delete() {
        CHUNK_SHADER.delete();
    }
}
