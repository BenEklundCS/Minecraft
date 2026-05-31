package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.world.ChunkPos;

// Plain data bag: produced by ChunkMesher on a worker thread, handed to GpuMesh on the GL thread.
public record ChunkMeshData(ChunkPos pos, float[] vertices, int[] indices, int vertexCount) {}
