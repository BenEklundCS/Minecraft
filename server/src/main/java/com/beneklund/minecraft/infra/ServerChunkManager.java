package com.beneklund.minecraft.infra;

import static com.beneklund.minecraft.util.Log.CHUNK;

import com.beneklund.minecraft.util.Threads;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// What exists on the server: loads, generates, evicts and saves chunks around a player.
public class ServerChunkManager {
    // Cap how many new chunks we kick off per tick so a large radius fills in over several
    // ticks instead of allocating + queueing the whole square at once. Spiral order means the
    // nearest missing chunks always win the budget first.
    private static final int MAX_LOADS_PER_TICK = 32;

    private final WorldConfig worldConfig;
    private final World world;
    private final IWorldGenerator generator;
    private final IChunkStore chunkStore;

    private ChunkCacheKey cache;
    private List<ChunkPos> lastChunksInRadius;

    private final ExecutorService generationPool;

    public ServerChunkManager(WorldConfig config, World world, IWorldGenerator generator, IChunkStore chunkStore) {
        worldConfig = config;
        this.world = world;
        this.generator = generator;
        this.chunkStore = chunkStore;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        generationPool = Executors.newFixedThreadPool(threads, Threads.namedFactory("chunk-generation-%d"));
        CHUNK.info("{} generation threads, load radius {}", threads, config.loadRadius());
    }

    // One pass of the load radius around a player.
    public void tick(ChunkPos playerPos) {
        List<ChunkPos> inRadius = getChunksInRadius(playerPos, worldConfig.loadRadius());
        Set<ChunkPos> keep = new HashSet<>(inRadius);

        int unloads = tickUnloads(keep);
        int loads = tickLoads(inRadius);

        if (CHUNK.isDebugEnabled() && (loads | unloads) != 0) {
            CHUNK.debug(
                    "tick @{}: +{} load, -{} unload, {} live",
                    playerPos,
                    loads,
                    unloads,
                    world.getChunkPositions().size());
        }
    }

    // LIVE only: a chunk still generating is an empty Chunk in World.
    public boolean replicable(ChunkPos pos) {
        Chunk chunk = world.getChunk(pos);
        return chunk != null && chunk.getState() == ChunkState.LIVE;
    }

    // Saves every live chunk that still needs persisting. Call after shutdown().
    public void flushAllDirty() {
        int saved = 0;
        for (var entry : world.getChunkEntries()) {
            Chunk chunk = entry.getValue();
            if (chunk.needsPersisting()) {
                chunkStore.save(entry.getKey(), chunk);
                chunk.clearNeedsPersisting();
                saved++;
            }
        }
        CHUNK.info("flushed {} dirty chunk(s) to disk", saved);
    }

    // Before flushAllDirty: an in-flight generate() can dirty a chunk that was already saved.
    public void shutdown(long timeoutSeconds) throws InterruptedException {
        CHUNK.debug("draining generation workers, {}s timeout", timeoutSeconds);
        generationPool.shutdown();
        if (!generationPool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            CHUNK.warn("generation drain timed out after {}s", timeoutSeconds);
        }
    }

    private int tickUnloads(Set<ChunkPos> keep) {
        int unloads = 0;
        for (ChunkPos pos : world.getChunkPositions()) {
            if (keep.contains(pos)) continue;
            Chunk chunk = world.getChunk(pos);
            // Refused only while GENERATING — the worker owns it; next tick tries again.
            if (!chunk.tryTransition(ChunkState.UNLOADING)) continue;
            if (chunk.needsPersisting()) {
                chunkStore.save(pos, chunk);
                chunk.clearNeedsPersisting();
            }
            world.removeChunk(pos);
            unloads++;
        }
        return unloads;
    }

    private int tickLoads(List<ChunkPos> inRadius) {
        int loads = 0;
        for (ChunkPos pos : inRadius) {
            if (loads >= MAX_LOADS_PER_TICK) break;
            if (world.hasChunk(pos)) continue;
            Optional<Chunk> saved = chunkStore.load(pos);
            Chunk chunk = saved.orElseGet(Chunk::new);
            world.addChunk(pos, chunk);
            if (saved.isPresent()) {
                chunk.tryTransition(ChunkState.LIVE);
            } else if (chunk.tryTransition(ChunkState.QUEUED_GEN)) {
                generationPool.execute(() -> generate(chunk, pos));
            }
            loads++;
        }
        return loads;
    }

    // Worker thread. Catches everything: a job that dies silently leaves its chunk stuck.
    private void generate(Chunk chunk, ChunkPos pos) {
        try {
            if (!chunk.tryTransition(ChunkState.GENERATING)) return;
            long startedAt = System.nanoTime();
            generator.generate(pos, worldConfig.seed(), chunk);
            // Clear before LIVE: after, it could wipe the flag an edit just set.
            chunk.clearNeedsPersisting();
            if (!chunk.tryTransition(ChunkState.LIVE)) return;
            CHUNK.trace("generated {} in {} us", pos, (System.nanoTime() - startedAt) / 1_000);
        } catch (Throwable t) {
            chunk.tryTransition(ChunkState.ERROR);
            CHUNK.error("generation job failed for chunk {}", pos, t);
        }
    }

    private record ChunkCacheKey(ChunkPos pos, int radius) {}

    //    # Source - https://stackoverflow.com/a/398302
    //    # Posted by Can Berk Güder, modified by community. See post 'Timeline' for change history
    //    # Retrieved 2026-05-31, License - CC BY-SA 3.0
    //
    //    def spiral(X, Y):
    //    x = y = 0
    //    dx = 0
    //    dy = -1
    //            for i in range(max(X, Y)**2):
    //            if (-X/2 < x <= X/2) and (-Y/2 < y <= Y/2):
    //    print (x, y)
    //            # DO STUFF...
    //            if x == y or (x < 0 and x == -y) or (x > 0 and x == 1-y):
    //    dx, dy = -dy, dx
    //    x, y = x+dx, y+dy

    // Package-private so ServerChunkManagerTest can check the cache hands back the same list.
    List<ChunkPos> getChunksInRadius(ChunkPos pos, int radius) {
        ChunkCacheKey key = new ChunkCacheKey(pos, radius);
        if (cache != null && cache.equals(key)) return lastChunksInRadius;
        List<ChunkPos> result = new ArrayList<>();
        int offsetX = 0;
        int offsetZ = 0;
        int stepX = 0;
        int stepZ = -1;
        int gridSize = (int) Math.pow((2 * radius + 1), 2);
        for (int i = 0; i < gridSize; i++) {
            if ((-radius <= offsetX && offsetX <= radius) && (-radius <= offsetZ && offsetZ <= radius)) {
                result.add(new ChunkPos(pos.x() + offsetX, pos.z() + offsetZ));
            }
            if (offsetX == offsetZ || (offsetX < 0 && offsetX == -offsetZ) || offsetX > 0 && offsetX == 1 - offsetZ) {
                int temp = -stepZ;
                stepZ = stepX;
                stepX = temp;
            }
            offsetX = offsetX + stepX;
            offsetZ = offsetZ + stepZ;
        }
        lastChunksInRadius = result;
        cache = key;
        return result;
    }
}
