package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

// Nine same-typed constructor arguments and a 3x3 array behind them — nothing here is
// compiler-checked, so the mapping gets pinned by identity instead. AO is the first caller that
// asks for a diagonal; before it, only the four cardinals were ever exercised.
class ChunkWithNeighborsTest {

    private final Chunk center = new Chunk();
    private final Chunk north = new Chunk();
    private final Chunk south = new Chunk();
    private final Chunk east = new Chunk();
    private final Chunk west = new Chunk();
    private final Chunk northEast = new Chunk();
    private final Chunk northWest = new Chunk();
    private final Chunk southEast = new Chunk();
    private final Chunk southWest = new Chunk();

    private final ChunkWithNeighbors cn =
            new ChunkWithNeighbors(center, north, south, east, west, northEast, northWest, southEast, southWest);

    // Local coords that land in each band: below the chunk, inside it, past the far edge.
    private static final int LOW = -1;
    private static final int MID = 5;
    private static final int HIGH = Chunk.SIZE_XZ;

    // resolve takes (x, z). North is z-1, south is z+1, east is x+1, west is x-1 — same
    // convention as NEIGHBOR_OFFSETS in ChunkMesher.
    @Test
    void resolve_mapsAllNineCells() {
        assertSame(northWest, cn.resolve(LOW, LOW).orElseThrow());
        assertSame(north, cn.resolve(MID, LOW).orElseThrow());
        assertSame(northEast, cn.resolve(HIGH, LOW).orElseThrow());
        assertSame(west, cn.resolve(LOW, MID).orElseThrow());
        assertSame(center, cn.resolve(MID, MID).orElseThrow());
        assertSame(east, cn.resolve(HIGH, MID).orElseThrow());
        assertSame(southWest, cn.resolve(LOW, HIGH).orElseThrow());
        assertSame(south, cn.resolve(MID, HIGH).orElseThrow());
        assertSame(southEast, cn.resolve(HIGH, HIGH).orElseThrow());
    }

    // The two coordinates either side of each band edge, since that's where an off-by-one lands.
    @Test
    void resolve_bandEdgesFallOnTheRightChunk() {
        assertSame(center, cn.resolve(0, 0).orElseThrow(), "0 is inside the chunk");
        assertSame(
                center,
                cn.resolve(Chunk.SIZE_XZ - 1, Chunk.SIZE_XZ - 1).orElseThrow(),
                "15 is the last in-chunk column");
        assertSame(northWest, cn.resolve(-1, -1).orElseThrow(), "-1 steps into the low neighbor on both axes");
        assertSame(
                southEast, cn.resolve(Chunk.SIZE_XZ, Chunk.SIZE_XZ).orElseThrow(), "16 steps into the high neighbor");
    }

    // An absent neighbor means "we don't know what's there", which is a different answer from
    // "it's air" — see isCulled in ChunkMesher for why the mesher needs to tell those apart.
    @Test
    void resolve_returnsEmptyForAnUnloadedNeighbor() {
        var onlyCenter = new ChunkWithNeighbors(center, null, null, null, null, null, null, null, null);

        assertTrue(onlyCenter.resolve(LOW, LOW).isEmpty());
        assertTrue(onlyCenter.resolve(HIGH, MID).isEmpty());
        assertSame(center, onlyCenter.resolve(MID, MID).orElseThrow(), "the center is still there");
    }

    @Test
    void center_isTheCenterChunk() {
        assertSame(center, cn.center());
    }

    // center() has no Optional because callers shouldn't have to check — the constructor is what
    // makes that true.
    @Test
    void constructor_rejectsANullCenter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkWithNeighbors(
                        null, north, south, east, west, northEast, northWest, southEast, southWest));
    }
}
