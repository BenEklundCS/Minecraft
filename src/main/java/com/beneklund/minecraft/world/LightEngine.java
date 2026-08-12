package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockRegistry;

public class LightEngine {
    private final BlockRegistry registry;

    public LightEngine(BlockRegistry registry) {
        this.registry = registry;
    }

    public LightMap compute(ChunkWithNeighbors cn) {
        return new LightMap(16);
    }
}
