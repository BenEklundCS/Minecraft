package com.beneklund.minecraft.world;

public record ChunkPos(int x, int z) {
    // Chunk-space step. Note this is not the same as offsetting world coordinates — one step
    // here is a whole chunk.
    public ChunkPos offset(int dx, int dz) {
        return new ChunkPos(x + dx, z + dz);
    }
}
