package com.beneklund.minecraft.infra;

import static com.beneklund.minecraft.util.Log.CHUNK;

import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// Tracks which chunks should be loaded based on player position and schedules
// load/unload work. Sits between the game loop and ChunkStore so the loop
// never blocks on I/O or generation.
public class ChunkManager {
    // Cap how many new chunks we kick off per tick so a large radius fills in over several
    // frames instead of allocating + queueing the whole square at once. Spiral order means
    // the nearest missing chunks always win the budget first. Real backpressure (bounded
    // upload queue, in-flight cap, eviction) is a later phase.
    private static final int MAX_LOADS_PER_TICK = 8;

    private final WorldConfig worldConfig;
    private final World world;
    private final IChunkStore chunkStore;

    private final ExecutorService generationPool;
    private final ExecutorService meshingPool;

    private final ConcurrentLinkedQueue<ChunkMeshData> uploadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkPos> unloadQueue = new ConcurrentLinkedQueue<>();

    private final Consumer<JobInput> genJob;
    private final Consumer<JobInput> meshJob;

    private record ChunkCacheKey(ChunkPos pos, int radius) {}

    private ChunkCacheKey cache;
    private List<ChunkPos> lastChunksInRadius;

    private final LightEngine lightEngine;

    public ChunkManager(
            WorldConfig config,
            World world,
            IWorldGenerator generator,
            ChunkMesher mesher,
            IWorldAuthority authority,
            IChunkStore chunkStore,
            LightEngine lightEngine) {
        worldConfig = config;
        this.world = world;
        this.chunkStore = chunkStore;
        this.lightEngine = lightEngine;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        generationPool = Executors.newFixedThreadPool(threads, namedFactory("chunk-generation-%d"));
        meshingPool = Executors.newFixedThreadPool(threads, namedFactory("chunk-meshing-%d"));
        CHUNK.info("{} generation + {} meshing threads, load radius {}", threads, threads, worldConfig.loadRadius());
        meshJob = (JobInput in) -> {
            try {
                if (!in.chunk.tryTransition(ChunkState.MESHING)) return;
                long startedAt = System.nanoTime();
                ChunkWithNeighbors cn = ChunkWithNeighbors.around(in.pos, this::meshable);
                in.chunk.setLightData(lightEngine.compute(cn));
                ChunkMeshData meshData = mesher.mesh(in.pos, cn);
                if (!in.chunk.tryTransition(ChunkState.READY_TO_UPLOAD)) return;
                uploadQueue.add(meshData);
                CHUNK.trace("meshed {} in {} us", in.pos, (System.nanoTime() - startedAt) / 1_000);
            } catch (Throwable t) {
                in.chunk.tryTransition(ChunkState.ERROR);
                CHUNK.error("mesh job failed for chunk {}", in.pos, t);
            }
        };
        genJob = (JobInput in) -> {
            try {
                if (!in.chunk.tryTransition(ChunkState.GENERATING)) return;
                long startedAt = System.nanoTime();
                generator.generate(in.pos, config.seed(), in.chunk);
                CHUNK.trace("generated {} in {} us", in.pos, (System.nanoTime() - startedAt) / 1_000);
                in.chunk.clearNeedsPersisting();
                authority.markNeighborsDirty(in.pos);
                if (!in.chunk.tryTransition(ChunkState.QUEUED_MESH)) return;
                meshingPool.execute(() -> meshJob.accept(new JobInput(in.chunk, in.pos)));
            } catch (Throwable t) {
                in.chunk.tryTransition(ChunkState.ERROR);
                CHUNK.error("generation job failed for chunk {}", in.pos, t);
            }
        };
    }

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

    public void tick(ChunkPos playerPos) {
        List<ChunkPos> chunkPositions = getChunksInRadius(playerPos, worldConfig.loadRadius());
        // UNLOAD
        int unloadsThisTick = 0;
        Set<ChunkPos> worldChunkPositionSet = world.getChunkPositions();
        Set<ChunkPos> nearbyChunkPositionSet = new HashSet<>(chunkPositions);
        for (ChunkPos worldPos : worldChunkPositionSet) {
            if (!nearbyChunkPositionSet.contains(worldPos)) {
                Chunk chunk = world.getChunk(worldPos);
                if (chunk.getState() == ChunkState.DIRTY) continue;
                if (!chunk.tryTransition(ChunkState.UNLOADING)) continue;
                if (chunk.needsPersisting()) {
                    chunkStore.save(worldPos, chunk);
                    chunk.clearNeedsPersisting();
                }
                world.removeChunk(worldPos);
                unloadQueue.add(worldPos);
                unloadsThisTick++;
            }
        }
        // LOAD: prefer saved chunks (skip gen, go straight to meshing); otherwise queue generation.
        // chunkStore.load runs on the game thread — single small file per chunk so the hit is small.
        // Move to a worker if it ever shows up in a frame profile.
        int loadsThisTick = 0;
        for (ChunkPos chunkPos : chunkPositions) {
            if (loadsThisTick >= MAX_LOADS_PER_TICK) break;
            if (world.hasChunk(chunkPos)) continue;
            Optional<Chunk> saved = chunkStore.load(chunkPos);
            Chunk chunk = saved.orElseGet(Chunk::new);
            world.addChunk(chunkPos, chunk);
            if (saved.isPresent()) {
                if (!chunk.tryTransition(ChunkState.QUEUED_MESH)) continue;
                meshingPool.execute(() -> meshJob.accept(new JobInput(chunk, chunkPos)));
            } else {
                if (!chunk.tryTransition(ChunkState.QUEUED_GEN)) continue;
                generationPool.execute(() -> genJob.accept(new JobInput(chunk, chunkPos)));
            }
            loadsThisTick++;
        }
        // DIRTY
        int remeshesThisTick = 0;
        for (var entry : world.getChunkEntries()) {
            Chunk chunk = entry.getValue();
            ChunkPos pos = entry.getKey();
            if (chunk.getState() == ChunkState.DIRTY) {
                if (chunk.tryTransition(ChunkState.QUEUED_MESH)) {
                    meshingPool.execute(() -> meshJob.accept(new JobInput(chunk, pos)));
                    remeshesThisTick++;
                }
            }
        }

        // tick() runs every frame, so a summary every frame would be a wall of noise. Only speak up
        // when the tick actually did something — during steady-state standing still, that's never.
        if (CHUNK.isDebugEnabled() && (loadsThisTick | unloadsThisTick | remeshesThisTick) != 0) {
            CHUNK.debug(
                    "tick @{}: +{} load, -{} unload, {} remesh, {} resident",
                    playerPos,
                    loadsThisTick,
                    unloadsThisTick,
                    remeshesThisTick,
                    world.getChunkPositions().size());
        }
    }

    // Stops accepting new jobs and waits up to timeoutSeconds for in-flight work to finish.
    // Drain generation fully before touching meshing: a gen job's last act is to submit a
    // mesh job, so shutting meshing down first would get those submissions rejected and
    // leave chunks stuck short of READY_TO_UPLOAD.
    public void shutdown(long timeoutSeconds) throws InterruptedException {
        CHUNK.debug("draining chunk workers, {}s timeout", timeoutSeconds);
        generationPool.shutdown();
        boolean genDone = generationPool.awaitTermination(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        meshingPool.shutdown();
        boolean meshDone = meshingPool.awaitTermination(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        // A timeout here is why a chunk edit sometimes doesn't survive a restart, so say so rather
        // than swallowing it — the pool is daemon-threaded and the JVM will exit regardless.
        if (!genDone || !meshDone) {
            CHUNK.warn("worker drain timed out (generation done={}, meshing done={})", genDone, meshDone);
        }
    }

    // Drains up to max items from the upload queue. Capped per frame to avoid hitching.
    public List<ChunkMeshData> drainUploadQueue(int max) {
        List<ChunkMeshData> batch = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            ChunkMeshData item = uploadQueue.poll();
            if (item == null) break;
            batch.add(item);
        }
        return batch;
    }

    // Drains all pending unload positions so the GL thread can free their GPU buffers.
    public List<ChunkPos> drainUnloadQueue() {
        List<ChunkPos> batch = new ArrayList<>();
        ChunkPos item;
        while ((item = unloadQueue.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }

    // tick() puts a chunk in the world map before generation runs, so a neighbor can be present
    // and still be entirely AIR. The mesher can't tell that apart from real air and would cull
    // against blocks that aren't there yet. Report it as null so it lands in isCulled's
    // unknown-neighbor path instead.
    private Chunk meshable(ChunkPos pos) {
        Chunk c = world.getChunk(pos);
        if (c == null) return null;
        return switch (c.getState()) {
            case UNLOADED, QUEUED_GEN, GENERATING -> null;
            default -> c;
        };
    }

    private record JobInput(Chunk chunk, ChunkPos pos) {}

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

    // Package-private, not private, so ChunkManagerTest can assert the cache hands back the same
    // list instance. Cache identity isn't observable through tick().
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

    private static ThreadFactory namedFactory(String pattern) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, String.format(pattern, n.getAndIncrement()));
            t.setDaemon(true);
            return t;
        };
    }
}
