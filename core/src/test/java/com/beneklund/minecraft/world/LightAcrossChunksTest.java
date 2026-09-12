package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import org.junit.jupiter.api.Test;

// Light crossing a chunk boundary, one direction per test. The center is roofed over its whole
// footprint so it has no sky column of its own — every lit cell in it had to arrive from a
// neighbour, which is what makes these fail rather than pass for the wrong reason.
class LightAcrossChunksTest {
    private static final int ROOF_Y = 70;
    private static final int PROBE_Y = 69; // the layer directly under the roof

    private final LightEngine engine = new LightEngine(BlockRegistry.createDefault());

    @Test
    void lightArrivesFromTheWestNeighbour() {
        assertEquals(14, crossedInto(Side.WEST), "x=0 is one step from the west neighbour's open column");
    }

    @Test
    void lightArrivesFromTheNorthNeighbour() {
        assertEquals(14, crossedInto(Side.NORTH), "z=0 is one step from the north neighbour's open column");
    }

    @Test
    void lightArrivesFromTheEastNeighbour() {
        assertEquals(14, crossedInto(Side.EAST), "x=15 is one step from the east neighbour's open column");
    }

    @Test
    void lightArrivesFromTheSouthNeighbour() {
        assertEquals(14, crossedInto(Side.SOUTH), "z=15 is one step from the south neighbour's open column");
    }

    private enum Side {
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    // Roofed center, one open neighbour on the named side, everything else absent. Answers the sky
    // level in the center's own border cell adjacent to that neighbour.
    private int crossedInto(Side side) {
        Chunk center = roofedChunk();
        Chunk open = litOpenChunk();

        ChunkWithNeighbors cn = new ChunkWithNeighbors(
                center,
                side == Side.NORTH ? open : null,
                side == Side.SOUTH ? open : null,
                side == Side.EAST ? open : null,
                side == Side.WEST ? open : null,
                null,
                null,
                null,
                null);

        LightMap map = engine.compute(cn);
        return switch (side) {
            case WEST -> map.sky(Chunk.index(0, PROBE_Y, 8));
            case EAST -> map.sky(Chunk.index(Chunk.SIZE_XZ - 1, PROBE_Y, 8));
            case NORTH -> map.sky(Chunk.index(8, PROBE_Y, 0));
            case SOUTH -> map.sky(Chunk.index(8, PROBE_Y, Chunk.SIZE_XZ - 1));
        };
    }

    private Chunk roofedChunk() {
        Chunk chunk = new Chunk();
        for (int x = 0; x < Chunk.SIZE_XZ; x++)
            for (int z = 0; z < Chunk.SIZE_XZ; z++) chunk.setBlock(x, ROOF_Y, z, Block.STONE);
        return chunk;
    }

    // An all-air chunk already lit, the way ChunkManager's mesh job leaves a neighbour before the
    // center is meshed. Without the LightMap actually populated the border read would be 0 and
    // these tests would fail for a reason that has nothing to do with direction.
    private Chunk litOpenChunk() {
        Chunk chunk = new Chunk();
        chunk.setLightData(engine.compute(ChunkWithNeighbors.noNeighbors(chunk)));
        return chunk;
    }
}
