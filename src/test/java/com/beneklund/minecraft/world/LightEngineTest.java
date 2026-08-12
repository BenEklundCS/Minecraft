package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import org.junit.jupiter.api.Test;

// Covers the column pass: walk each (x,z) down from the sky and light every cell until an
// opaque block stops it. Reads back through LightMap.block() because that's the channel
// computeColumns writes.
class LightEngineTest {
    private final LightEngine engine = new LightEngine(BlockRegistry.createDefault());

    private LightMap lightOf(Chunk chunk) {
        return engine.compute(ChunkWithNeighbors.noNeighbors(chunk));
    }

    private int lightAt(LightMap map, int x, int y, int z) {
        return map.sky(Chunk.index(x, y, z));
    }

    // nothing in the way anywhere, so every cell in the chunk sees the sky
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

    // the opaque block itself is the first cell the walk refuses to light, and it stops there —
    // everything under it stays at MIN_LEVEL even though it's also air
    @Test
    void opaqueBlock_stopsColumnAtItsOwnHeight() {
        Chunk chunk = new Chunk();
        chunk.setBlock(4, 100, 7, Block.STONE);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 4, 101, 7));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 4, 100, 7));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 4, 99, 7));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 4, 0, 7));
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

    // a block at the very top means the walk never lights a single cell in that column
    @Test
    void opaqueAtTopOfChunk_leavesColumnDark() {
        Chunk chunk = new Chunk();
        chunk.setBlock(9, Chunk.SIZE_Y - 1, 9, Block.STONE);

        LightMap map = lightOf(chunk);

        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            assertEquals(LightMap.MIN_LEVEL, lightAt(map, 9, y, 9), "y=" + y);
        }
    }

    // columns are walked independently — blocking one must not darken its neighbours
    @Test
    void blockedColumn_doesNotAffectNeighbours() {
        Chunk chunk = new Chunk();
        chunk.setBlock(8, 128, 8, Block.STONE);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 8, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 7, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 9, 64, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 64, 7));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 64, 9));
    }

    // a flat floor across the whole chunk: lit above, dark from the floor down
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

    // with two blockers in one column, the walk quits at the higher one and never reaches the lower
    @Test
    void multipleOpaqueBlocks_columnStopsAtHighest() {
        Chunk chunk = new Chunk();
        chunk.setBlock(2, 200, 3, Block.STONE);
        chunk.setBlock(2, 50, 3, Block.DIRT);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 2, 201, 3));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 2, 200, 3));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 2, 150, 3));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 2, 50, 3));
    }
}
