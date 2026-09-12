package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LightMapTest {
    private static final int LEVEL_COUNT = 16;

    @Test
    public void lightMapSetAndGet() {
        LightMap lightMap = new LightMap();

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
        LightMap lightMap = new LightMap();
        for (int i = 0; i < Chunk.SIZE_Y; i++) {
            lightMap.setSky(i, i % LEVEL_COUNT);
            lightMap.setBlock(i, i % LEVEL_COUNT);
        }
        for (int i = 0; i < Chunk.SIZE_Y; i++) {
            assertEquals(i % LEVEL_COUNT, lightMap.sky(i));
            assertEquals(i % LEVEL_COUNT, lightMap.block(i));
        }
    }

    @Test
    void compact_doesNotChangeAnythingReadBack() {
        LightMap map = new LightMap();
        for (int i = 0; i < Chunk.size(); i++) {
            map.setSky(i, i / ChunkSection.BLOCK_COUNT >= 8 ? LightMap.MAX_LEVEL : 0); // top half open sky
        }
        int[] skyBefore = new int[Chunk.size()];
        int[] blockBefore = new int[Chunk.size()];
        for (int i = 0; i < Chunk.size(); i++) {
            skyBefore[i] = map.sky(i);
            blockBefore[i] = map.block(i);
        }

        map.compact();

        for (int i = 0; i < Chunk.size(); i++) {
            assertEquals(skyBefore[i], map.sky(i), "sky changed at " + i);
            assertEquals(blockBefore[i], map.block(i), "block changed at " + i);
        }
        map.setBlock(Chunk.index(0, 200, 0), 7); // re-materialize a compacted section
        assertEquals(LightMap.MAX_LEVEL, map.sky(Chunk.index(1, 200, 0)), "neighbours kept their sky through the fill");
    }

    // The stage is only worth anything if uniform sections actually give their arrays back. Without
    // this, compact() could be a no-op and the round-trip test above would still pass.
    @Test
    void compact_dropsTheArrayOfEverySectionThatEndedUpUniform() {
        LightMap map = new LightMap();
        for (int i = 0; i < Chunk.size(); i++) {
            map.setSky(i, i / ChunkSection.BLOCK_COUNT >= 8 ? LightMap.MAX_LEVEL : 0);
        }
        assertEquals(8, map.materializedSections(), "the eight lit sections allocated, the dark ones never did");

        map.compact();

        assertEquals(0, map.materializedSections(), "every section was uniform, so none should still hold an array");
    }

    // A section that is genuinely mixed has to keep its array, or compact() is losing data.
    @Test
    void compact_keepsTheArrayOfAMixedSection() {
        LightMap map = new LightMap();
        map.setSky(Chunk.index(0, 100, 0), LightMap.MAX_LEVEL);

        map.compact();

        assertEquals(1, map.materializedSections());
        assertEquals(LightMap.MAX_LEVEL, map.sky(Chunk.index(0, 100, 0)));
        assertEquals(0, map.sky(Chunk.index(1, 100, 0)), "its neighbour in the same section stays dark");
    }

    @Test
    void fillSky_setsAWholeSectionWithoutMaterialisingIt() {
        LightMap map = new LightMap();
        map.fillSky(1, LightMap.MAX_LEVEL);
        assertEquals(0, map.materializedSections());
        assertEquals(LightMap.MAX_LEVEL, map.sky(ChunkSection.BLOCK_COUNT));
        assertEquals(LightMap.MAX_LEVEL, map.sky(ChunkSection.BLOCK_COUNT * 2 - 1));
        assertEquals(0, map.block(ChunkSection.BLOCK_COUNT)); // block channel untouched
        assertEquals(0, map.sky(0)); // section 0 untouched
    }
}
