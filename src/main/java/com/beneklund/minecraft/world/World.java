package com.beneklund.minecraft.world;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.input.InputHandler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    private final InputHandler inputHandler;
    // ConcurrentHashMap because chunk load/unload happens on worker threads while the
    // render thread reads this map every frame.
    private final ConcurrentHashMap<ChunkPos, Chunk> chunks;

    public World(ConcurrentHashMap<ChunkPos, Chunk> chunks, InputHandler inputHandler) {
        this.chunks = chunks;
        this.inputHandler = inputHandler;
    }

    public void update(List<IInputAction> actions, float dt) {
        this.inputHandler.handle(actions);
    }

    public Chunk getChunk(ChunkPos pos) {
        return chunks.get(pos);
    }

    public void addChunk(ChunkPos pos, Chunk chunk) {
        this.chunks.put(pos, chunk);
    }

    public void removeChunk(ChunkPos pos) {
        this.chunks.remove(pos);
    }

    public boolean hasChunk(ChunkPos pos) {
        return this.chunks.containsKey(pos);
    }

    public Set<ChunkPos> getChunkPositions() {
        return this.chunks.keySet();
    }

    public Set<Map.Entry<ChunkPos, Chunk>> getChunkEntries() {
        return this.chunks.entrySet();
    }
}
