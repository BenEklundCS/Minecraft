package com.beneklund.minecraft.world;

public record ChunkPos(int x, int z) {
    // Chunk-space step. Note this is not the same as offsetting world coordinates — one step
    // here is a whole chunk.
    public ChunkPos offset(int dx, int dz) {
        return new ChunkPos(x + dx, z + dz);
    }

    // The chunk a block coordinate falls in. floorDiv, not /: / rounds toward zero, which puts
    // x = -1 in chunk 0 alongside x = 1 instead of in chunk -1.
    public static ChunkPos containing(int x, int z) {
        return new ChunkPos(Math.floorDiv(x, Chunk.SIZE_XZ), Math.floorDiv(z, Chunk.SIZE_XZ));
    }

    // A position rather than a block. Floor before the cast: (int) rounds toward zero, which puts
    // x = -0.5 in block 0 instead of block -1.
    public static ChunkPos containing(float x, float z) {
        return containing((int) Math.floor(x), (int) Math.floor(z));
    }
}
