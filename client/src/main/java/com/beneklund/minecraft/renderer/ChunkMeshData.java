package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Geometry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

/**
 * One chunk's mesh as plain arrays: built by {@link ChunkMesher} on a worker thread, uploaded into
 * a {@code ChunkMesh} on the main thread.
 *
 * <p>Geometry comes split by pass so opaque blocks draw first and blended blocks (water, glass)
 * second; see {@link RenderPass}. {@code chunk} rides along so the main thread can advance its
 * state after the upload. {@code vertexCount} totals both halves.
 */
public record ChunkMeshData(ChunkPos pos, Geometry opaque, Geometry transparent, int vertexCount, Chunk chunk) {}
