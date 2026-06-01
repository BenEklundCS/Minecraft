package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

// Plain data bag: produced by ChunkMesher on a worker thread, handed to GpuMesh on the GL thread.
// Carries the Chunk reference so the GL thread can advance its state to UPLOADED after the upload.
public record ChunkMeshData(ChunkPos pos, float[] vertices, int[] indices, int vertexCount, Chunk chunk) {}
