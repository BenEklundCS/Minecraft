package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.DrawCall;
import com.beneklund.minecraft.renderer.Frustum;
import com.beneklund.minecraft.renderer.IRenderable;
import com.beneklund.minecraft.renderer.ShaderProgram;
import com.beneklund.minecraft.renderer.TextureAtlas;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

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
        for (var entry : renderWorld.getEntries()) {
            ChunkPos pos = entry.getKey();
            float minX = pos.x() * Chunk.SIZE_XZ;
            float minZ = pos.z() * Chunk.SIZE_XZ;
            AABB bounds = new AABB(minX, 0, minZ, minX + Chunk.SIZE_XZ, Chunk.SIZE_Y, minZ + Chunk.SIZE_XZ);
            if (!frustum.isVisible(bounds)) continue;
            result.add(new DrawCall(entry.getValue(), new Matrix4f().translation(minX, 0, minZ), CHUNK_SHADER));
        }
        return result;
    }

    @Override
    public void delete() {
        CHUNK_SHADER.delete();
    }
}
