package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LightMapTest {
    private static final int LEVEL_COUNT = 16;

    @Test
    public void lightMapSetAndGet() {
        LightMap lightMap = new LightMap(Chunk.SIZE_XZ);

        int idx = 10;
        int sky = 5;
        int block = 3;
        lightMap.setSky(idx, sky);
        lightMap.setBlock(idx, block);
        assertEquals(sky, lightMap.sky(idx));
        assertEquals(block, lightMap.block(idx));
        int newSky = 7;
        lightMap.setSky(idx, newSky);
        assertEquals(block, lightMap.block(idx));
        lightMap.setBlock(idx, 9);
        assertEquals(newSky, lightMap.sky(idx));
        int maxLevel = LEVEL_COUNT - 1;
        lightMap.setSky(idx, maxLevel);
        lightMap.setBlock(idx, maxLevel);
        assertEquals(maxLevel, lightMap.sky(idx));
        assertEquals(maxLevel, lightMap.block(idx));
    }

    @Test
    public void useFullLightMap() {
        LightMap lightMap = new LightMap(Chunk.size());
        assertEquals(Chunk.size(), lightMap.size());
        for (int i = 0; i < Chunk.SIZE_Y; i++) {
            lightMap.setSky(i, i % LEVEL_COUNT);
            lightMap.setBlock(i, i % LEVEL_COUNT);
        }
        for (int i = 0; i < Chunk.SIZE_Y; i++) {
            assertEquals(i % LEVEL_COUNT, lightMap.sky(i));
            assertEquals(i % LEVEL_COUNT, lightMap.block(i));
        }
    }
}
