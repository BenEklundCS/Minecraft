package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.platform.graphics.ChunkMesh;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.Collection;
import java.util.HashMap;
import org.joml.Matrix4f;

/**
 * The uploaded chunk meshes, keyed by chunk position. Main thread only, because creating and
 * deleting a {@link ChunkMesh} are GL calls; meshing workers hand their output to {@code
 * ClientChunkManager}'s upload queue and never reach this class.
 *
 * <p>Each entry carries its model matrix and world-space bounds, computed once at upload, so the
 * renderer translates and frustum-culls a chunk without recomputing either per frame. {@link
 * #version()} counts every add, replace and remove, which lets the shadow pass reuse a cascade
 * when nothing it could see has changed.
 */
public class RenderWorld {
    /**
     * One chunk's GPU meshes. Either mesh is {@code null} when the chunk has no geometry of that
     * kind: an all-stone chunk has no transparent mesh, an all-air chunk has neither.
     */
    public record Entry(ChunkMesh opaqueMesh, ChunkMesh transparentMesh, Matrix4f model, AABB bounds) {
        public void delete() {
            if (opaqueMesh != null) opaqueMesh.delete();
            if (transparentMesh != null) transparentMesh.delete();
        }
    }

    private final HashMap<ChunkPos, Entry> meshes = new HashMap<>();

    /**
     * Bumped whenever the set of meshes changes, so a consumer can tell "exactly as I last saw it"
     * from "something moved" without diffing the map.
     *
     * <p>The shadow pass skips redrawing a cascade whose contents can't have changed. That is only
     * sound if every mutation bumps this: miss one and a cascade keeps showing terrain that is gone,
     * which reads as a shadow with no caster.
     */
    private int version;

    /** Stores a chunk's meshes, deleting any meshes they replace. */
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

    /** Removes and returns the entry, or {@code null}; the caller deletes its meshes. */
    public Entry remove(ChunkPos pos) {
        Entry removed = meshes.remove(pos);
        if (removed != null) version++;
        return removed;
    }

    public Collection<Entry> getEntries() {
        return meshes.values();
    }

    /** Changes whenever a mesh is added, replaced or removed. See the field for the invariant. */
    public int version() {
        return version;
    }
}
