package com.beneklund.minecraft.world.chunk;

import com.beneklund.minecraft.block.Block;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A chunk and its eight horizontal neighbours as a 3x3 grid, addressed by coordinates local to
 * the centre.
 *
 * <p>Meshing and lighting both read one block past the chunk edge: face culling and ambient
 * occlusion need the neighbouring block, and light crosses seams. Here, a local {@code x} or
 * {@code z} of -1 or 16 resolves to the right neighbour in one step, so callers never compute
 * which chunk a cell belongs to. The grid is indexed {@code [x][z]}, 0 being the low neighbour on
 * that axis (west, north) and 2 the high one (east, south).
 *
 * <p>The centre is required. Any neighbour may be {@code null} for a chunk that isn't loaded, and
 * reads into it return the defaults documented on each getter.
 *
 * <p>It holds references, not copies. Other threads keep writing to the chunks while a meshing
 * worker reads them, so a read can be stale; that costs one wrong face, and the edit's remesh
 * corrects it. The design relies on that remesh in place of locking.
 */
public class ChunkWithNeighbors {
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

    /**
     * Gathers the grid around {@code pos}. The offsets live here, next to the array layout they
     * must match. {@code lookup} returns {@code null} for a chunk that isn't loaded or ready,
     * which the neighbour slots accept.
     *
     * @throws IllegalArgumentException if {@code lookup} has no chunk at {@code pos}
     */
    public static ChunkWithNeighbors around(ChunkPos pos, IChunkLookup lookup) {
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

    /** Every present neighbour, in no promised order. Use {@link #resolve} when position matters. */
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

    /** The block at centre-local coordinates; air outside the column or in a missing neighbour. */
    public Block blockAt(int centerLocalX, int y, int centerLocalZ) {
        Chunk chunk = resolveInBounds(centerLocalX, y, centerLocalZ);
        if (chunk == null) return Block.AIR;
        return chunk.getBlock(local(centerLocalX), y, local(centerLocalZ));
    }

    /**
     * Sky light at centre-local coordinates, as a raw level from 0 to 15; callers averaging
     * several samples divide once at the end.
     *
     * <p>Below the world it returns 0. Above the column, in a missing neighbour, or in a neighbour
     * with blocks but no {@link LightMap} yet, it returns 15. A missing neighbour is the render
     * edge, and reading it as dark paints a black seam along the chunk boundary until the neighbour
     * arrives. Reading an unlit neighbour as 0 is what once painted black faces on trees standing
     * on a chunk seam.
     */
    public int skyLightAt(int centerLocalX, int y, int centerLocalZ) {
        if (y < 0) return LightMap.MIN_LEVEL;
        Chunk chunk = resolveInBounds(centerLocalX, y, centerLocalZ);
        if (chunk == null || !chunk.hasLight()) return LightMap.MAX_LEVEL;
        return chunk.getSkyLight(local(centerLocalX), y, local(centerLocalZ));
    }

    /**
     * Block light at centre-local coordinates, 0 to 15. Out of range, missing or unlit, it returns
     * 0, the opposite default to {@link #skyLightAt}: a missing chunk usually means open sky, and
     * it never means a torch. Guessing bright here would light sealed rooms along every chunk
     * border.
     */
    public int blockLightAt(int centerLocalX, int y, int centerLocalZ) {
        Chunk chunk = resolveInBounds(centerLocalX, y, centerLocalZ);
        if (chunk == null || !chunk.hasLight()) return LightMap.MIN_LEVEL;
        return chunk.getBlockLight(local(centerLocalX), y, local(centerLocalZ));
    }

    /**
     * {@link #blockAt(int, int, int)} at a {@code {dx, dy, dz}} offset. The offset overloads let a
     * caller walking a list of offsets make every read about one cell look identical, so an offset
     * can't be applied to one lookup and forgotten on the next.
     */
    public Block blockAt(int centerLocalX, int y, int centerLocalZ, int[] off) {
        return blockAt(centerLocalX + off[0], y + off[1], centerLocalZ + off[2]);
    }

    public int skyLightAt(int centerLocalX, int y, int centerLocalZ, int[] off) {
        return skyLightAt(centerLocalX + off[0], y + off[1], centerLocalZ + off[2]);
    }

    public int blockLightAt(int centerLocalX, int y, int centerLocalZ, int[] off) {
        return blockLightAt(centerLocalX + off[0], y + off[1], centerLocalZ + off[2]);
    }

    /**
     * The chunk holding a centre-local column. Any value below 0 maps to the low neighbour and any
     * value from 16 up to the high one, so coordinates are only meaningful from -16 to 31.
     */
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

    private Chunk resolveInBounds(int centerLocalX, int y, int centerLocalZ) {
        if (!Chunk.inYRange(y)) return null;
        return resolve(centerLocalX, centerLocalZ).orElse(null);
    }

    private static int local(int i) {
        return Math.floorMod(i, Chunk.SIZE_XZ);
    }
}
