package com.beneklund.minecraft.world.chunk;

import java.util.Optional;

/** Persistent chunk storage, keyed by position. */
public interface IChunkStore {
    void save(ChunkPos pos, Chunk chunk);

    /** The saved chunk at {@code pos}, or empty if none was saved or the save can't be read. */
    Optional<Chunk> load(ChunkPos pos);
}
