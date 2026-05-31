package com.beneklund.minecraft.renderer;

// Plain data bag: produced by ChunkMesher on a worker thread, handed to GpuMesh on the GL thread.
public record ChunkMeshData(float[] vertices, int[] indices, int vertexCount) {}
