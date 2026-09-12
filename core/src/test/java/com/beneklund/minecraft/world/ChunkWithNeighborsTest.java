package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
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
        var onlyCenter = ChunkWithNeighbors.noNeighbors(center);

        assertTrue(onlyCenter.resolve(LOW, LOW).isEmpty());
        assertTrue(onlyCenter.resolve(HIGH, MID).isEmpty());
        assertSame(center, onlyCenter.resolve(MID, MID).orElseThrow(), "the center is still there");
    }

    @Test
    void center_isTheCenterChunk() {
        assertSame(center, cn.center());
    }

    // around() is the only production construction path, and its offset table is the one thing
    // that can silently disagree with the array layout above. Built away from the origin on
    // purpose — at (0,0) broken chunk arithmetic and correct chunk arithmetic often land on the
    // same cell, which is how the floorDiv bug in markNeighborsDirty stayed hidden.
    @Test
    void around_wiresEveryOffsetToTheRightSlot() {
        ChunkPos origin = new ChunkPos(4, -7);
        Map<ChunkPos, Chunk> loaded = Map.of(
                origin,
                center,
                origin.offset(0, -1),
                north,
                origin.offset(0, 1),
                south,
                origin.offset(1, 0),
                east,
                origin.offset(-1, 0),
                west,
                origin.offset(1, -1),
                northEast,
                origin.offset(-1, -1),
                northWest,
                origin.offset(1, 1),
                southEast,
                origin.offset(-1, 1),
                southWest);

        var built = ChunkWithNeighbors.around(origin, loaded::get);

        assertSame(center, built.center());
        assertSame(northWest, built.resolve(LOW, LOW).orElseThrow());
        assertSame(north, built.resolve(MID, LOW).orElseThrow());
        assertSame(northEast, built.resolve(HIGH, LOW).orElseThrow());
        assertSame(west, built.resolve(LOW, MID).orElseThrow());
        assertSame(east, built.resolve(HIGH, MID).orElseThrow());
        assertSame(southWest, built.resolve(LOW, HIGH).orElseThrow());
        assertSame(south, built.resolve(MID, HIGH).orElseThrow());
        assertSame(southEast, built.resolve(HIGH, HIGH).orElseThrow());
    }

    @Test
    void neighbors_returnsTheEightSurroundingChunks() {
        List<Chunk> found = cn.neighbors();

        assertEquals(8, found.size());
        assertFalse(found.contains(center), "a chunk is not its own neighbor");
        assertTrue(found.containsAll(List.of(north, south, east, west, northEast, northWest, southEast, southWest)));
    }

    // markNeighborsDirty iterates this, so an unloaded neighbor has to drop out rather than
    // arrive as a null to trip over.
    @Test
    void neighbors_omitsUnloadedOnes() {
        var sparse = new ChunkWithNeighbors(center, north, null, null, null, null, null, null, southWest);

        List<Chunk> found = sparse.neighbors();

        assertEquals(2, found.size());
        assertTrue(found.containsAll(List.of(north, southWest)));
    }
}
