package com.beneklund.minecraft.world;

import java.util.Optional;

public interface IChunkStore {
    void save(ChunkPos pos, Chunk chunk);

    Optional<Chunk> load(ChunkPos pos);
}
