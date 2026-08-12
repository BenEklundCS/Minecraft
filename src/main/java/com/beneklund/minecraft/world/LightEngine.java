package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockRegistry;

public class LightEngine {
    private final BlockRegistry registry;

    public LightEngine(BlockRegistry registry) {
        this.registry = registry;
    }

    public LightMap compute(ChunkWithNeighbors cn) {
        LightMap lightMap = new LightMap(Chunk.size());
        computeColumns(cn.center(), lightMap);
        return lightMap;
    }

    private void computeColumns(Chunk chunk, LightMap map) {
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                int y = Chunk.SIZE_Y - 1;
                while (y >= 0 && !registry.get(chunk.getBlock(x, y, z)).opaque()) {
                    map.setSky(Chunk.index(x, y, z), LightMap.MAX_LEVEL);
                    y--;
                }
            }
        }
    }
}
