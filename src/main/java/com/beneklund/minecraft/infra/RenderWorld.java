package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.Collection;
import java.util.HashMap;
import org.joml.Matrix4f;

// Main-thread-only store of uploaded chunk meshes. Workers never touch this —
// GpuMesh creation and deletion must happen on the GL thread.
public class RenderWorld {
    // opaqueMesh / transparentMesh may be null when a chunk has no geometry of that kind
    // (e.g. an all-stone chunk has no transparent mesh; an all-air chunk has neither).
    public record Entry(GpuMesh opaqueMesh, GpuMesh transparentMesh, Matrix4f model, AABB bounds) {}

    private final HashMap<ChunkPos, Entry> meshes = new HashMap<>();

    // Computes and stores the model matrix and bounds once at upload time.
    public void add(ChunkPos pos, GpuMesh opaqueMesh, GpuMesh transparentMesh) {
        float x = pos.x() * Chunk.SIZE_XZ;
        float z = pos.z() * Chunk.SIZE_XZ;
        meshes.put(
                pos,
                new Entry(
                        opaqueMesh,
                        transparentMesh,
                        new Matrix4f().translation(x, 0, z),
                        new AABB(x, 0, z, x + Chunk.SIZE_XZ, Chunk.SIZE_Y, z + Chunk.SIZE_XZ)));
    }

    // Removes and returns the entry so the caller can delete its GL buffers.
    public Entry remove(ChunkPos pos) {
        return meshes.remove(pos);
    }

    public Collection<Entry> getEntries() {
        return meshes.values();
    }
}
