package com.beneklund.minecraft.world;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.input.InputHandler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// World exists to safely manage a concurrent hashmap of chunks
public class World {
    // TODO: Remove inputHandler to decouple input from World's concurrency
    private final InputHandler inputHandler;

    private final ConcurrentHashMap<ChunkPos, Chunk> chunks;

    public World(ConcurrentHashMap<ChunkPos, Chunk> chunks, InputHandler inputHandler) {
        this.chunks = chunks;
        this.inputHandler = inputHandler;
    }

    public void update(List<IInputAction> actions, float dt) {
        inputHandler.handle(actions);
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
