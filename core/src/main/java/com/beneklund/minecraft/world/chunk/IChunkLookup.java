package com.beneklund.minecraft.world.chunk;

/**
 * Finds the chunk at a position, or {@code null} when there is none the caller may use. Each
 * caller decides what "usable" means, e.g. loaded, or loaded with blocks.
 */
public interface IChunkLookup {
    Chunk at(ChunkPos pos);
}
