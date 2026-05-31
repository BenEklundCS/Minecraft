package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

public interface IWorldGenerator {
    Chunk generate(ChunkPos pos, long seed);
}
