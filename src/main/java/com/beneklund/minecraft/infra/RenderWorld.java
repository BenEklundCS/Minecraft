package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Main-thread-only store of uploaded chunk meshes. Workers never touch this —
// GpuMesh creation and deletion must happen on the GL thread.
public class RenderWorld {
    private final HashMap<ChunkPos, GpuMesh> meshes = new HashMap<>();

    // Stores an uploaded mesh keyed by chunk position. Overwrites any previous mesh at that pos.
    public void add(ChunkPos pos, GpuMesh mesh) {
        meshes.put(pos, mesh);
    }

    // Removes and returns the mesh so the caller can delete its GL buffers.
    public GpuMesh remove(ChunkPos pos) {
        return meshes.remove(pos);
    }

    public Collection<GpuMesh> getAll() {
        return meshes.values();
    }

    // Used by ChunkRenderer to get pos alongside mesh for per-chunk model matrix.
    public Set<Map.Entry<ChunkPos, GpuMesh>> getEntries() {
        return meshes.entrySet();
    }
}
