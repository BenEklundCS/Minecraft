package com.beneklund.minecraft.world;

import java.util.Optional;
import org.joml.Vector2i;

// stores a chunk with its neighbors, north south east west, used to in one-step resolve the chunk we are indexing
// into from local coords
// central chunk must never be null, but all neighbors can be
public class ChunkWithNeighbors {
    private static final Vector2i CENTER = new Vector2i(1, 1);
    private final Chunk[][] chunks;

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

    // resolve translates a center local x and local z chunk coordinate into the correct chunk
    // assume centerLocalX and centerLocalZ are either 0-15 (in center) or -1 or 16 (off center)
    public Optional<Chunk> resolve(int centerLocalX, int centerLocalZ) {
        int chunksX = normalize(centerLocalX);
        int chunksZ = normalize(centerLocalZ);
        return wrap(chunks[chunksX][chunksZ]);
    }

    public Chunk center() {
        return chunks[CENTER.x()][CENTER.y()];
    }

    private Optional<Chunk> wrap(Chunk c) {
        return Optional.ofNullable(c);
    }

    private int normalize(int i) {
        if (i < 0) {
            return 0;
        } else if (i < Chunk.SIZE_XZ) {
            return 1;
        }
        return 2;
    }
}
