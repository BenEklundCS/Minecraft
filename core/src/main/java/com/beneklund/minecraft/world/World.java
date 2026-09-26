package com.beneklund.minecraft.world;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The set of loaded chunks by position, safe to read and write from any thread.
 *
 * <p>Presence in the map says nothing about readiness. The server inserts an empty chunk before
 * generation fills it, so readers gate on {@link Chunk#getState()}.
 */
public class World {
    private final ConcurrentHashMap<ChunkPos, Chunk> chunks;

    public World(ConcurrentHashMap<ChunkPos, Chunk> chunks) {
        this.chunks = chunks;
    }

    public Chunk getChunk(ChunkPos pos) {
        return chunks.get(pos);
    }

    public void addChunk(ChunkPos pos, Chunk chunk) {
        chunks.put(pos, chunk);
    }

    public void removeChunk(ChunkPos pos) {
        chunks.remove(pos);
    }

    public boolean hasChunk(ChunkPos pos) {
        return chunks.containsKey(pos);
    }

    /** A live view of the map's keys; iteration is weakly consistent with concurrent edits. */
    public Set<ChunkPos> getChunkPositions() {
        return chunks.keySet();
    }

    /** A live view of the map's entries; iteration is weakly consistent with concurrent edits. */
    public Set<Map.Entry<ChunkPos, Chunk>> getChunkEntries() {
        return chunks.entrySet();
    }
}
