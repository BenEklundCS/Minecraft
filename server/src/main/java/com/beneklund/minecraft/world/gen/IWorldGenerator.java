package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;

/** Fills a chunk's blocks from its position and the world seed. */
public interface IWorldGenerator {
    /** Writes the generated blocks for {@code pos} into {@code chunk}, which starts empty. */
    void generate(ChunkPos pos, long seed, Chunk chunk);
}
