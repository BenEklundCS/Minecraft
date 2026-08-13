package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import org.junit.jupiter.api.Test;

// Scenes are sealed stone boxes rather than relying on the chunk edge to hold light in, so they
// survive light crossing into neighbouring chunks. lightDoesNotWrapAroundChunkEdges is the one
// exception, and it will need revisiting when that lands.
class LightFloodFillTest {
    private final LightEngine engine = new LightEngine(BlockRegistry.createDefault());

    @Test
    void skylightThroughRoofHole_spreadsSidewaysOneLevelPerStep() {
        Chunk chunk = sealedRoom(60, 70);
        chunk.setBlock(8, 70, 8, Block.AIR);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 69, 8));
        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 61, 8));

        assertEquals(14, lightAt(map, 7, 65, 8));
        assertEquals(13, lightAt(map, 6, 65, 8));
        assertEquals(12, lightAt(map, 5, 65, 8));
        assertEquals(13, lightAt(map, 7, 65, 7)); // diagonal is two steps, not one

        assertEquals(12, lightAt(map, 5, 61, 8));
        assertEquals(12, lightAt(map, 5, 69, 8));

        assertEquals(1, lightAt(map, 1, 65, 1)); // 14 steps out
        assertEquals(3, lightAt(map, 14, 65, 14)); // 12 steps out
    }

    // Levels in the shadow follow the shortest path light can walk, not the distance to the
    // source — decrementing by distance would put (8,61,5) at 12.
    @Test
    void wallInsideRoom_castsShadowLightRoutesAround() {
        Chunk chunk = sealedRoom(60, 62); // interior is the single plane y=61
        chunk.setBlock(8, 62, 8, Block.AIR);
        for (int x = 5; x <= 11; x++) {
            chunk.setBlock(x, 61, 6, Block.STONE);
        }

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 61, 8));
        assertEquals(14, lightAt(map, 8, 61, 7)); // one step, on the lit side of the wall
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 8, 61, 6)); // the wall itself
        assertEquals(9, lightAt(map, 4, 61, 6)); // six steps, round the wall's end

        // three cells away in a straight line; both routes round the wall are eleven steps
        assertEquals(4, lightAt(map, 8, 61, 5));
    }

    // The gap is offset from the roof hole so light reaches it already below 15, which is what
    // pins the downward exception to "already at 15" rather than "moving down".
    @Test
    void lightBelowFullStrength_dimsOneLevelPerStepDown() {
        Chunk chunk = sealedRoom(60, 80);
        chunk.setBlock(8, 80, 8, Block.AIR);
        fillLayer(chunk, 70, Block.STONE);
        chunk.setBlock(5, 70, 5, Block.AIR);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 71, 8));
        assertEquals(9, lightAt(map, 5, 71, 5)); // six steps out

        assertEquals(8, lightAt(map, 5, 70, 5));
        assertEquals(7, lightAt(map, 5, 69, 5));
        assertEquals(6, lightAt(map, 5, 68, 5));
        assertEquals(3, lightAt(map, 5, 65, 5));
        assertEquals(1, lightAt(map, 5, 63, 5));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 5, 62, 5));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 5, 61, 5));

        assertEquals(6, lightAt(map, 4, 69, 5)); // spreads sideways off the falling column
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 14, 65, 14)); // far corner, nothing reaches
    }

    // Green today — its job is to stay green if the flood ever leaks through opaque blocks.
    @Test
    void sealedRoom_staysDark() {
        Chunk chunk = sealedRoom(60, 100);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 101, 8)); // above the roof, sky as usual
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 14; z++) {
                for (int y = 61; y <= 99; y++) {
                    assertEquals(LightMap.MIN_LEVEL, lightAt(map, x, y, z), "sealed at " + x + "," + y + "," + z);
                }
            }
        }
    }

    // Unwalled, so light reaches the chunk edge. index + 1 at x=15 is a legal index — (0, y, z+1)
    // — so a wrap would write 7 into (0,61,9), whose honest answer is 6.
    @Test
    void lightDoesNotWrapAroundChunkEdges() {
        Chunk chunk = new Chunk();
        fillLayer(chunk, 60, Block.STONE);
        fillLayer(chunk, 62, Block.STONE);
        chunk.setBlock(8, 62, 8, Block.AIR);

        LightMap map = lightOf(chunk);

        assertEquals(LightMap.MAX_LEVEL, lightAt(map, 8, 61, 8));
        assertEquals(8, lightAt(map, 15, 61, 8)); // seven steps east, hard against the edge
        assertEquals(7, lightAt(map, 0, 61, 8)); // eight steps west
        assertEquals(6, lightAt(map, 0, 61, 9)); // nine steps, not the 7 a wrap would write
        assertEquals(1, lightAt(map, 15, 61, 15));
        assertEquals(LightMap.MIN_LEVEL, lightAt(map, 0, 61, 0)); // sixteen steps, past the falloff
    }

    // Guards the queue staying a local: one LightEngine is shared across meshing threads, so a
    // reused buffer held as a field would make the second call disagree.
    @Test
    void computeIsIdempotent() {
        Chunk chunk = sealedRoom(60, 80);
        chunk.setBlock(8, 80, 8, Block.AIR);
        fillLayer(chunk, 70, Block.STONE);
        chunk.setBlock(5, 70, 5, Block.AIR);

        LightMap first = lightOf(chunk);
        LightMap second = lightOf(chunk);

        for (int i = 0; i < Chunk.size(); i++) {
            assertEquals(first.sky(i), second.sky(i), "index " + i);
        }
    }

    // Interior is x,z in 1..14 and y strictly between the two. Tests punch their own holes.
    private Chunk sealedRoom(int floorY, int roofY) {
        Chunk chunk = new Chunk();
        fillLayer(chunk, floorY, Block.STONE);
        fillLayer(chunk, roofY, Block.STONE);
        for (int y = floorY + 1; y < roofY; y++) {
            for (int i = 0; i < Chunk.SIZE_XZ; i++) {
                chunk.setBlock(0, y, i, Block.STONE);
                chunk.setBlock(Chunk.SIZE_XZ - 1, y, i, Block.STONE);
                chunk.setBlock(i, y, 0, Block.STONE);
                chunk.setBlock(i, y, Chunk.SIZE_XZ - 1, Block.STONE);
            }
        }
        return chunk;
    }

    private void fillLayer(Chunk chunk, int y, Block block) {
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                chunk.setBlock(x, y, z, block);
            }
        }
    }

    private LightMap lightOf(Chunk chunk) {
        return engine.compute(ChunkWithNeighbors.noNeighbors(chunk));
    }

    private int lightAt(LightMap map, int x, int y, int z) {
        return map.sky(Chunk.index(x, y, z));
    }
}
