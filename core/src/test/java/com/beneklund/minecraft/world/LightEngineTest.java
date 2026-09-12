package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import org.junit.jupiter.api.Test;

// The column pass seen through the finished engine, which runs the flood straight afterwards — so
// a stopped column reads 14, refilled from the open columns beside it, rather than 0. Its not
// being 15 is what proves the walk stopped. LightFloodFillTest covers the flood itself.
class LightEngineTest {
    private static final int REFILLED_FROM_NEIGHBOURS = LightMap.MAX_LEVEL - 1;

    private final LightEngine engine = new LightEngine(BlockRegistry.createDefault());

    private LightMap lightOf(Chunk chunk) {
        return engine.compute(ChunkWithNeighbors.noNeighbors(chunk));
    }

    private int lightAt(LightMap map, int x, int y, int z) {
        return map.sky(Chunk.index(x, y, z));
    }

    @Test
    void allAirChunk_isFullyLit() {
        LightMap map = lightOf(new Chunk());

        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    assertEquals(LightMap.MAX_LEVEL, lightAt(map, x, y, z), "air at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void opaqueBlock_stopsColumnAtItsOwnHeight() {
        Chunk chunk = new Chunk();
        chunk.setBlock(4, 100, 7, Block.STONE);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 4, 101, 7));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 4, 100, 7));
        assertEquals(REFILLED_FROM_NEIGHBOURS, lightAt(map, 4, 99, 7));
        assertEquals(REFILLED_FROM_NEIGHBOURS, lightAt(map, 4, 0, 7));
    }

    // transparent() beats solid() in BlockDef.opaque(), so leaves and glass don't cast a column shadow
    @Test
    void transparentBlocks_doNotStopColumn() {
        Chunk chunk = new Chunk();
        chunk.setBlock(1, 80, 1, Block.OAK_LEAF); // solid, transparent
        chunk.setBlock(1, 79, 1, Block.GLASS); // non-solid, transparent

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 1, 80, 1));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 1, 79, 1));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 1, 0, 1));
    }

    @Test
    void opaqueAtTopOfChunk_columnNeverStarts() {
        Chunk chunk = new Chunk();
        chunk.setBlock(9, Chunk.SIZE_Y - 1, 9, Block.STONE);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 9, Chunk.SIZE_Y - 1, 9));
        for (int y = 0; y < Chunk.SIZE_Y - 1; y++) {
            assertEquals(REFILLED_FROM_NEIGHBOURS, lightAt(map, 9, y, 9), "y=" + y);
        }
    }

    @Test
    void blockedColumn_doesNotAffectNeighbours() {
        Chunk chunk = new Chunk();
        chunk.setBlock(8, 128, 8, Block.STONE);

        LightMap map = lightOf(chunk);

        assertEquals(REFILLED_FROM_NEIGHBOURS, lightAt(map, 8, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 7, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 9, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 64, 7));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 64, 9));
    }

    // the floor spans the whole chunk, so there is nowhere for the flood to come in from
    @Test
    void flatFloor_litAboveDarkBelow() {
        Chunk chunk = new Chunk();
        int floorY = 60;
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                chunk.setBlock(x, floorY, z, Block.GRASS);
            }
        }

        LightMap map = lightOf(chunk);

        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                assertEquals(LightMap.MAX_LEVEL, lightAt(map, x, floorY + 1, z));
                assertEquals(LightMap.MIN_LEVEL, lightAt(map, x, floorY, z));
                assertEquals(LightMap.MIN_LEVEL, lightAt(map, x, floorY - 1, z));
            }
        }
    }

    @Test
    void multipleOpaqueBlocks_columnStopsAtHighest() {
        Chunk chunk = new Chunk();
        chunk.setBlock(2, 200, 3, Block.STONE);
        chunk.setBlock(2, 50, 3, Block.DIRT);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 2, 201, 3));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 2, 200, 3));
        assertEquals(REFILLED_FROM_NEIGHBOURS, lightAt(map, 2, 150, 3));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 2, 50, 3));
    }
}
