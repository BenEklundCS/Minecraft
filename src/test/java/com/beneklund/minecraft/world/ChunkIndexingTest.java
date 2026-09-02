package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.util.Direction;
import org.junit.jupiter.api.Test;

// Pins Chunk's addressing, to prevent regression from shift to ChunkSection API
class ChunkIndexingTest {

    private static byte[] payloadWithLandmarks() {
        byte[] payload = new byte[Chunk.size()];
        payload[Chunk.index(0, 0, 0)] = Block.BEDROCK.id(); // origin
        payload[Chunk.index(15, 15, 15)] = Block.COBBLE.id(); // last block of section 0
        payload[Chunk.index(0, 16, 0)] = Block.STONE.id(); // first block of section 1
        payload[Chunk.index(5, 64, 9)] = Block.DIAMOND_ORE.id(); // mid-chunk, all three axes distinct
        payload[Chunk.index(5, 200, 9)] = Block.GLOWSTONE.id(); // high section, same x/z as above
        payload[Chunk.index(15, 255, 15)] = Block.GLASS.id(); // max corner
        return payload;
    }

    // The layout statement everything else is derived from: x is contiguous, z steps by one row of
    // x, y steps by one full xz plane. Change any of these three and the save format changes with it.
    @Test
    void index_stridesAreOneThenSixteenThen256() {
        assertEquals(0, Chunk.index(0, 0, 0));
        assertEquals(1, Chunk.index(1, 0, 0) - Chunk.index(0, 0, 0), "x stride");
        assertEquals(16, Chunk.index(0, 0, 1) - Chunk.index(0, 0, 0), "z stride");
        assertEquals(256, Chunk.index(0, 1, 0) - Chunk.index(0, 0, 0), "y stride");
    }

    @Test
    void index_knownCoordinatesHaveKnownValues() {
        assertEquals(15, Chunk.index(15, 0, 0));
        assertEquals(240, Chunk.index(0, 0, 15));
        assertEquals(16533, Chunk.index(5, 64, 9));
        assertEquals(51349, Chunk.index(5, 200, 9));
        assertEquals(Chunk.size() - 1, Chunk.index(15, 255, 15), "the max corner is the last slot");
    }

    // No collisions and nothing outside [0, size()) — the property that lets a caller hand an index
    // straight to LightMap as an array slot.
    @Test
    void index_isABijectionOntoTheChunk() {
        boolean[] seen = new boolean[Chunk.size()];
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                    int index = Chunk.index(x, y, z);
                    assertTrue(index >= 0 && index < Chunk.size(), "%d,%d,%d out of range".formatted(x, y, z));
                    assertFalse(seen[index], "%d,%d,%d collided at %d".formatted(x, y, z, index));
                    seen[index] = true;
                }
            }
        }
    }

    @Test
    void xyzDecoders_invertIndex() {
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                    int index = Chunk.index(x, y, z);
                    assertEquals(x, Chunk.x(index));
                    assertEquals(y, Chunk.y(index));
                    assertEquals(z, Chunk.z(index));
                }
            }
        }
    }

    // The identity the section storage rests on: a whole-chunk index splits into (which section,
    // where inside it) on the 4096 boundary with no coordinate math at all. If this stops holding,
    // serialize, deserialize and both getBlock overloads are wrong simultaneously.
    @Test
    void index_splitsIntoSectionAndOffsetOnTheBlockCountBoundary() {
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                    int index = Chunk.index(x, y, z);
                    assertEquals(y / ChunkSection.SIZE, index / ChunkSection.BLOCK_COUNT, "section of y=" + y);
                    assertEquals(
                            ChunkSection.index(x, y % ChunkSection.SIZE, z),
                            index % ChunkSection.BLOCK_COUNT,
                            "offset within section for %d,%d,%d".formatted(x, y, z));
                }
            }
        }
    }

    @Test
    void sectionBoundary_fallsBetweenY15AndY16() {
        assertEquals(ChunkSection.BLOCK_COUNT - 1, Chunk.index(15, 15, 15), "last block of section 0");
        assertEquals(ChunkSection.BLOCK_COUNT, Chunk.index(0, 16, 0), "first block of section 1");
        assertEquals(0, Chunk.index(15, 15, 15) / ChunkSection.BLOCK_COUNT);
        assertEquals(1, Chunk.index(0, 16, 0) / ChunkSection.BLOCK_COUNT);
    }

    @Test
    void size_matchesTheSectionArrayItDescribes() {
        assertEquals(65536, Chunk.size());
        assertEquals(Chunk.size(), (Chunk.SIZE_Y / ChunkSection.SIZE) * ChunkSection.BLOCK_COUNT);
    }

    // The on-disk contract: byte n of a payload is the block at whatever coordinates index() maps to
    // n. This is what let sectioning land without a ChunkStore VERSION bump.
    @Test
    void payload_isAddressedByIndex() {
        Chunk chunk = new Chunk(payloadWithLandmarks());

        assertEquals(Block.BEDROCK, chunk.getBlock(0, 0, 0));
        assertEquals(Block.COBBLE, chunk.getBlock(15, 15, 15));
        assertEquals(Block.STONE, chunk.getBlock(0, 16, 0));
        assertEquals(Block.DIAMOND_ORE, chunk.getBlock(5, 64, 9));
        assertEquals(Block.GLOWSTONE, chunk.getBlock(5, 200, 9));
        assertEquals(Block.GLASS, chunk.getBlock(15, 255, 15));
        assertEquals(Block.AIR, chunk.getBlock(1, 1, 1), "untouched cells stay air");
    }

    @Test
    void serialize_reproducesThePayloadItWasBuiltFrom() {
        byte[] payload = payloadWithLandmarks();
        assertArrayEquals(payload, new Chunk(payload).serialize());
    }

    @Test
    void serialize_isOneBytePerBlock() {
        assertEquals(Chunk.size(), new Chunk().serialize().length);
        assertEquals(Chunk.size(), new Chunk(payloadWithLandmarks()).serialize().length);
    }

    @Test
    void serialize_ofAnUntouchedChunkIsAllAir() {
        for (byte id : new Chunk().serialize()) {
            assertEquals(Block.AIR.id(), id);
        }
    }

    // The two overloads are two spellings of one address. Checked over the whole chunk because a
    // wrong section or a wrong offset only shows up at particular y values.
    @Test
    void getBlock_bothOverloadsAgreeEverywhere() {
        Chunk chunk = new Chunk(payloadWithLandmarks());
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                    assertEquals(
                            chunk.getBlock(x, y, z),
                            chunk.getBlock(Chunk.index(x, y, z)),
                            "disagreement at %d,%d,%d".formatted(x, y, z));
                }
            }
        }
    }

    // setBlock has to land at the address getBlock reads and serialize writes. Built from a payload
    // whose section 4 (y 64..79) already holds something, so this pins where the write goes without
    // depending on how a missing section gets allocated.
    @Test
    void setBlock_landsAtTheSameIndexGetBlockAndSerializeUse() {
        byte[] payload = new byte[Chunk.size()];
        payload[Chunk.index(0, 64, 0)] = Block.STONE.id();
        Chunk chunk = new Chunk(payload);

        chunk.setBlock(5, 70, 9, Block.DIAMOND_ORE);

        assertEquals(Block.DIAMOND_ORE, chunk.getBlock(5, 70, 9));
        assertEquals(Block.DIAMOND_ORE, chunk.getBlock(Chunk.index(5, 70, 9)));
        assertEquals(Block.DIAMOND_ORE, Block.fromId(chunk.serialize()[Chunk.index(5, 70, 9)]));
        assertEquals(Block.STONE, chunk.getBlock(0, 64, 0), "the earlier block in the same section survives");
    }

    @Test
    void neighborIndex_stepsOneBlockPerDirection() {
        int index = Chunk.index(8, 128, 8);
        assertEquals(Chunk.index(8, 129, 8), Chunk.neighborIndex(index, Direction.UP));
        assertEquals(Chunk.index(8, 127, 8), Chunk.neighborIndex(index, Direction.DOWN));
        assertEquals(Chunk.index(8, 128, 7), Chunk.neighborIndex(index, Direction.NORTH));
        assertEquals(Chunk.index(8, 128, 9), Chunk.neighborIndex(index, Direction.SOUTH));
        assertEquals(Chunk.index(9, 128, 8), Chunk.neighborIndex(index, Direction.EAST));
        assertEquals(Chunk.index(7, 128, 8), Chunk.neighborIndex(index, Direction.WEST));
    }

    // -1 rather than a wrapped index is what stops a walk off one face reappearing on the opposite
    // one — a step off the edge is the mesher's cue to go ask the neighbouring chunk instead.
    @Test
    void neighborIndex_isMinusOneOffEveryFace() {
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(0, 0, 0), Direction.DOWN));
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(0, 255, 0), Direction.UP));
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(0, 0, 0), Direction.NORTH));
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(0, 0, 15), Direction.SOUTH));
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(0, 0, 0), Direction.WEST));
        assertEquals(-1, Chunk.neighborIndex(Chunk.index(15, 0, 0), Direction.EAST));
    }

    // A section boundary is not a chunk boundary: stepping up out of section 0 stays in the chunk.
    @Test
    void neighborIndex_crossesSectionBoundariesWithoutStopping() {
        assertEquals(Chunk.index(3, 16, 5), Chunk.neighborIndex(Chunk.index(3, 15, 5), Direction.UP));
        assertEquals(Chunk.index(3, 15, 5), Chunk.neighborIndex(Chunk.index(3, 16, 5), Direction.DOWN));
    }

    @Test
    void bounds_acceptTheChunkAndRejectOneStepOutside() {
        assertTrue(Chunk.inBounds(0, 0, 0));
        assertTrue(Chunk.inBounds(15, 255, 15));
        assertFalse(Chunk.inBounds(-1, 0, 0));
        assertFalse(Chunk.inBounds(16, 0, 0));
        assertFalse(Chunk.inBounds(0, -1, 0));
        assertFalse(Chunk.inBounds(0, 256, 0));
        assertFalse(Chunk.inBounds(0, 0, -1));
        assertFalse(Chunk.inBounds(0, 0, 16));
    }
}
