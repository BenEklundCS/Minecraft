package com.beneklund.minecraft.world;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// World exists to safely manage a concurrent hashmap of chunks
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

    public Set<ChunkPos> getChunkPositions() {
        return chunks.keySet();
    }

    public Set<Map.Entry<ChunkPos, Chunk>> getChunkEntries() {
        return chunks.entrySet();
    }
}
