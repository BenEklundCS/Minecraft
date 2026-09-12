package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

public interface IWorldGenerator {
    void generate(ChunkPos pos, long seed, Chunk chunk);
}
