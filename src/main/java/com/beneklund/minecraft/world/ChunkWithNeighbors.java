package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// stores a chunk with its neighbors, north south east west, (and diagonals), used to in one-step resolve the chunk we
// are indexing
// into from local coords
// central chunk must never be null, but all neighbors can be
//
// Snapshot of references, not of contents. The Chunks behind these keep being written by other
// threads while a meshing worker reads them, so a stale read is possible — it costs one wrong
// face, corrected on the next remesh. Don't add locking here; the remesh is the correction.
public class ChunkWithNeighbors {
    // Index of the center row and column in the 3x3 below. Not a coordinate — 0 is the low
    // neighbor on that axis, 2 is the high one.
    private static final int CENTER = 1;

    private final Chunk[][] chunks;

    // Public because tests build these from nine chunks they already have in hand, which is the
    // readable shape for pinning the layout. Production never calls this — around() does.
    public ChunkWithNeighbors(
            Chunk chunk,
            Chunk north,
            Chunk south,
            Chunk east,
            Chunk west,
            Chunk northEast,
            Chunk northWest,
            Chunk southEast,
            Chunk southWest) {
        if (chunk == null) throw new IllegalArgumentException("Center chunk must not be null.");
        chunks = new Chunk[][] {{northWest, west, southWest}, {north, chunk, south}, {northEast, east, southEast}};
    }

    // The offsets live here rather than at the call sites so they can't drift from the array
    // above. lookup answers with null for a chunk that isn't loaded or isn't ready yet, which
    // the neighbor slots accept and the center does not.
    public static ChunkWithNeighbors around(ChunkPos pos, ChunkLookup lookup) {
        return new ChunkWithNeighbors(
                lookup.at(pos),
                lookup.at(pos.offset(0, -1)), // NORTH
                lookup.at(pos.offset(0, 1)), // SOUTH
                lookup.at(pos.offset(1, 0)), // EAST
                lookup.at(pos.offset(-1, 0)), // WEST
                lookup.at(pos.offset(1, -1)), // NORTH EAST
                lookup.at(pos.offset(-1, -1)), // NORTH WEST
                lookup.at(pos.offset(1, 1)), // SOUTH EAST
                lookup.at(pos.offset(-1, 1))); // SOUTH WEST
    }

    public static ChunkWithNeighbors noNeighbors(Chunk chunk) {
        return new ChunkWithNeighbors(chunk, null, null, null, null, null, null, null, null);
    }

    // Every neighbor that's actually there, for callers that want to touch all of them rather
    // than locate one. Order isn't promised — use resolve() if position matters.
    public List<Chunk> neighbors() {
        List<Chunk> neighbors = new ArrayList<>();
        for (int x = 0; x < chunks.length; x++) {
            for (int z = 0; z < chunks[x].length; z++) {
                if (x == CENTER && z == CENTER) continue;
                if (chunks[x][z] != null) neighbors.add(chunks[x][z]);
            }
        }
        return neighbors;
    }

    public Block blockAt(int centerLocalX, int y, int centerLocalZ) {
        // SIZE_Y is the length, so 255 is the last valid index. At y == 256 Chunk.index() lands
        // exactly one past the end of the array and getBlock throws — it has no bounds guard.
        if (y < 0 || y >= Chunk.SIZE_Y) return Block.AIR;
        Chunk chunk = resolve(centerLocalX, centerLocalZ).orElse(null);
        if (chunk == null) return Block.AIR;
        return chunk.getBlock(
                Math.floorMod(centerLocalX, Chunk.SIZE_XZ), y, Math.floorMod(centerLocalZ, Chunk.SIZE_XZ));
    }

    // resolve translates a center local x and local z chunk coordinate into the correct chunk
    // assume centerLocalX and centerLocalZ are either 0-15 (in center) or -1 or 16 (off center)
    public Optional<Chunk> resolve(int centerLocalX, int centerLocalZ) {
        int chunksX = normalize(centerLocalX);
        int chunksZ = normalize(centerLocalZ);
        return wrap(chunks[chunksX][chunksZ]);
    }

    public Chunk center() {
        return chunks[CENTER][CENTER];
    }

    private Optional<Chunk> wrap(Chunk c) {
        return Optional.ofNullable(c);
    }

    private int normalize(int i) {
        return (i < 0) ? 0 : (i < Chunk.SIZE_XZ) ? 1 : 2;
    }
}
