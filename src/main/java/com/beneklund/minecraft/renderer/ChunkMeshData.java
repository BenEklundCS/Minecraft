package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Geometry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

// Plain data bag: produced by ChunkMesher on a worker thread, handed to GpuMesh on the GL thread.
// Carries the Chunk reference so the GL thread can advance its state to UPLOADED after the upload.
//
// Geometry is split into two buffers up front so the renderer can draw opaque blocks first
// (depth write on) and transparent blocks (water, glass, leaves) second (depth write off, blending).
// vertexCount is the combined total across both buffers — used by tests as a sanity check.
public record ChunkMeshData(ChunkPos pos, Geometry opaque, Geometry transparent, int vertexCount, Chunk chunk) {}
