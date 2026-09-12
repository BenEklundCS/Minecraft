package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.platform.graphics.ChunkMesh;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.Collection;
import java.util.HashMap;
import org.joml.Matrix4f;

// Main-thread-only store of uploaded chunk meshes. Workers never touch this —
// ChunkMesh creation and deletion must happen on the GL thread.
public class RenderWorld {
    // opaqueMesh / transparentMesh may be null when a chunk has no geometry of that kind
    // (e.g. an all-stone chunk has no transparent mesh; an all-air chunk has neither).
    public record Entry(ChunkMesh opaqueMesh, ChunkMesh transparentMesh, Matrix4f model, AABB bounds) {
        public void delete() {
            if (opaqueMesh != null) opaqueMesh.delete();
            if (transparentMesh != null) transparentMesh.delete();
        }
    }

    private final HashMap<ChunkPos, Entry> meshes = new HashMap<>();

    /*
     * Bumped whenever the set of meshes changes. Lets a consumer tell "the world is exactly as I
     * last saw it" from "something moved", without diffing the map.
     *
     * The shadow pass uses it to skip redrawing a cascade whose contents cannot have changed. That
     * is only sound if every mutation is counted here — miss one and a cascade keeps showing
     * terrain that is no longer there, which reads as a shadow with no caster.
     */
    private int version;

    // Computes and stores the model matrix and bounds once at upload time.
    public void add(ChunkPos pos, ChunkMesh opaqueMesh, ChunkMesh transparentMesh) {
        float x = pos.x() * Chunk.SIZE_XZ;
        float z = pos.z() * Chunk.SIZE_XZ;
        Entry previousMesh = meshes.put(
                pos,
                new Entry(
                        opaqueMesh,
                        transparentMesh,
                        new Matrix4f().translation(x, 0, z),
                        new AABB(x, 0, z, x + Chunk.SIZE_XZ, Chunk.SIZE_Y, z + Chunk.SIZE_XZ)));
        if (previousMesh != null) {
            previousMesh.delete();
        }
        version++;
    }

    // Removes and returns the entry so the caller can delete its GL buffers.
    public Entry remove(ChunkPos pos) {
        Entry removed = meshes.remove(pos);
        if (removed != null) version++;
        return removed;
    }

    public Collection<Entry> getEntries() {
        return meshes.values();
    }

    // Changes whenever a mesh is added, replaced or removed. See the field.
    public int version() {
        return version;
    }
}
