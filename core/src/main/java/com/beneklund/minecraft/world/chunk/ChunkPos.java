package com.beneklund.minecraft.world.chunk;

/** A chunk's column coordinate: world block coordinates divided by 16, rounded down. */
public record ChunkPos(int x, int z) {
    /** The chunk {@code dx} chunks east and {@code dz} chunks south; one step is 16 blocks. */
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
