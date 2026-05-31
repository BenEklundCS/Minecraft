package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class WorldTest {
    // inputHandler is unused by the storage methods under test, so null is fine here
    private World emptyWorld() {
        return new World(new ConcurrentHashMap<>(), null);
    }

    // addChunk then getChunk hands back the exact instance we stored
    @Test
    void addChunk_thenGetChunk_returnsSameInstance() {
        World world = emptyWorld();
        ChunkPos pos = new ChunkPos(2, -3);
        Chunk chunk = new Chunk();
        world.addChunk(pos, chunk);
        assertSame(chunk, world.getChunk(pos));
    }

    // removeChunk drops it - a later lookup is null
    @Test
    void removeChunk_thenGetChunk_returnsNull() {
        World world = emptyWorld();
        ChunkPos pos = new ChunkPos(2, -3);
        world.addChunk(pos, new Chunk());
        world.removeChunk(pos);
        assertNull(world.getChunk(pos));
    }
}
