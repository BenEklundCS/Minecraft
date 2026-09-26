package com.beneklund.minecraft.infra;

import static com.beneklund.minecraft.util.Log.CHUNK;

import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.renderer.chunk.ChunkMeshData;
import com.beneklund.minecraft.renderer.chunk.ChunkMesher;
import com.beneklund.minecraft.util.Threads;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.chunk.ChunkState;
import com.beneklund.minecraft.world.chunk.ChunkWithNeighbors;
import com.beneklund.minecraft.world.chunk.LightEngine;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The client's replica of the world, filled only by server packets, and the meshing pipeline that
 * turns it into GPU-ready geometry.
 *
 * <p>Three packet handlers write the replica: {@link #onChunkData} inserts a chunk with its blocks,
 * {@link #onChunkUnload} removes one, {@link #onBlockChanged} applies an edit. The server decides
 * what the client holds; the client never loads or evicts on its own. Because a chunk enters with
 * its blocks, presence alone means "has blocks" ({@link #hasBlocks}).
 *
 * <p>Meshing runs on a fixed pool of {@code max(2, cores / 2)} threads. A job gathers the chunk and
 * its eight neighbours into a {@link ChunkWithNeighbors}, computes light with the {@link
 * LightEngine}, meshes, and queues plain {@link ChunkMeshData}. The main thread drains that queue
 * under a per-frame budget and uploads; workers never touch GL. Each step goes through {@link
 * Chunk#tryTransition}, so a job whose chunk was unloaded or re-dirtied mid-flight loses the race
 * and drops its result instead of publishing a stale mesh. {@link #drainUploadQueue} also drops a
 * mesh whose chunk is no longer the one in the replica.
 *
 * <p>Edits re-dirty the chunk, and the neighbours too when the edit sits on a chunk border or
 * removes a light source, since a neighbour's faces and light depend on this chunk's blocks.
 * {@link #tick()} requeues everything {@code DIRTY}.
 *
 * @see ChunkState
 */
public class ClientChunkManager {
    private final World world;
    private final ChunkMesher mesher;
    private final LightEngine lightEngine;
    private final BlockRegistry registry;
    // The same replica as an IWorldView, for LightEngine's removal walk.
    private final IWorldView view;

    private final ExecutorService meshingPool;

    private final ConcurrentLinkedQueue<ChunkMeshData> uploadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkPos> unloadQueue = new ConcurrentLinkedQueue<>();

    public ClientChunkManager(
            World world, ChunkMesher mesher, LightEngine lightEngine, BlockRegistry registry, IWorldView view) {
        this.world = world;
        this.mesher = mesher;
        this.lightEngine = lightEngine;
        this.registry = registry;
        this.view = view;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        meshingPool = Executors.newFixedThreadPool(threads, Threads.namedFactory("chunk-meshing-%d"));
        CHUNK.info("{} meshing threads", threads);
    }

    /**
     * Inserts a streamed chunk and queues its mesh. Neighbours remesh too, or they keep culling
     * against the air this chunk used to be.
     */
    public void onChunkData(IPacket.ToClient.ChunkData packet) {
        Chunk chunk = new Chunk(packet.blocks());
        ChunkPos pos = packet.pos();
        world.addChunk(pos, chunk);
        if (chunk.tryTransition(ChunkState.QUEUED_MESH)) meshingPool.execute(() -> mesh(chunk, pos));
        markNeighborsDirty(pos);
    }

    /**
     * Removes a chunk from the replica and queues its position so the main thread frees its GPU
     * meshes. The only way a chunk leaves the replica.
     */
    public void onChunkUnload(IPacket.ToClient.ChunkUnload packet) {
        ChunkPos pos = packet.pos();
        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return;
        chunk.tryTransition(ChunkState.UNLOADING); // can fail, unload anyway
        world.removeChunk(pos);
        unloadQueue.add(pos);
    }

    /**
     * Applies a server-confirmed edit and marks what must remesh. An edit to a chunk the client
     * doesn't hold is dropped, because the chunk will arrive with the edit already in its blocks.
     */
    public void onBlockChanged(IPacket.ToClient.BlockChanged packet) {
        int x = packet.x(), y = packet.y(), z = packet.z();
        if (!Chunk.inYRange(y)) return;
        ChunkPos pos = ChunkPos.containing(x, z);
        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return; // it'll arrive already edited
        // floorMod, not %: x = -1 is local 15 in chunk -1, and % would give -1.
        int lx = Math.floorMod(x, Chunk.SIZE_XZ);
        int lz = Math.floorMod(z, Chunk.SIZE_XZ);
        int removedLight = registry.get(chunk.getBlock(lx, y, lz)).lightLevel();
        chunk.setBlock(lx, y, lz, packet.block());
        chunk.tryTransition(ChunkState.DIRTY);
        // A removed emitter's glow is baked into its neighbours' LightMaps; a remesh would read it back.
        if (removedLight > 0) {
            lightEngine.removeBlockLight(view, x, y, z, removedLight);
            markNeighborsDirty(pos);
        } else if (lx == 0 || lx == Chunk.SIZE_XZ - 1 || lz == 0 || lz == Chunk.SIZE_XZ - 1) {
            markNeighborsDirty(pos);
        }
    }

    /** Queues every {@code DIRTY} chunk onto the meshing pool. */
    public void tick() {
        for (var entry : world.getChunkEntries()) {
            Chunk chunk = entry.getValue();
            ChunkPos pos = entry.getKey();
            if (chunk.getState() == ChunkState.DIRTY && chunk.tryTransition(ChunkState.QUEUED_MESH)) {
                meshingPool.execute(() -> mesh(chunk, pos));
            }
        }
    }

    /**
     * Takes up to {@code max} finished meshes, skipping any built for a chunk that has since been
     * unloaded or replaced. {@code Game} passes its per-frame upload budget, so a burst of meshes
     * spreads over several frames instead of hitching one.
     */
    public List<ChunkMeshData> drainUploadQueue(int max) {
        List<ChunkMeshData> out = new ArrayList<>();
        ChunkMeshData data;
        while (out.size() < max && (data = uploadQueue.poll()) != null) {
            // A mesh for a chunk that was unloaded or replaced while it was being built.
            if (world.getChunk(data.pos()) != data.chunk()) continue;
            out.add(data);
        }
        return out;
    }

    /** Takes every pending unload, for the main thread to free their GPU meshes. */
    public List<ChunkPos> drainUnloadQueue() {
        List<ChunkPos> res = new ArrayList<>();
        while (!unloadQueue.isEmpty()) {
            res.add(unloadQueue.poll());
        }
        return res;
    }

    /** Whether the replica holds {@code pos}; a chunk only enters the replica with its blocks. */
    public boolean hasBlocks(ChunkPos pos) {
        return world.getChunk(pos) != null;
    }

    /** Runs on a meshing worker. Any throw parks the chunk in {@code ERROR} and logs it. */
    private void mesh(Chunk chunk, ChunkPos pos) {
        try {
            if (!chunk.tryTransition(ChunkState.MESHING)) return;
            long startedAt = System.nanoTime();
            // The chunk as queued, not looked up: the server may have unloaded it since.
            ChunkWithNeighbors cn = ChunkWithNeighbors.around(pos, p -> p.equals(pos) ? chunk : meshable(p));
            chunk.setLightData(lightEngine.compute(cn));
            ChunkMeshData meshData = mesher.mesh(pos, cn);
            if (!chunk.tryTransition(ChunkState.READY_TO_UPLOAD)) return;
            uploadQueue.add(meshData);
            CHUNK.trace("meshed {} in {} us", pos, (System.nanoTime() - startedAt) / 1_000);
        } catch (Throwable t) {
            chunk.tryTransition(ChunkState.ERROR);
            CHUNK.error("mesh job failed for chunk {}", pos, t);
        }
    }

    // Every chunk in the replica has its blocks, so anything present is meshable.
    private Chunk meshable(ChunkPos pos) {
        return world.getChunk(pos);
    }

    private void markNeighborsDirty(ChunkPos pos) {
        for (Chunk n : ChunkWithNeighbors.around(pos, world::getChunk).neighbors()) {
            n.tryTransition(ChunkState.DIRTY);
        }
    }

    /** Stops accepting mesh jobs and waits up to {@code timeoutSeconds} for running ones to finish. */
    public void shutdown(long timeoutSeconds) throws InterruptedException {
        CHUNK.debug("draining meshing workers, {}s timeout", timeoutSeconds);
        meshingPool.shutdown();
        if (!meshingPool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            CHUNK.warn("meshing drain timed out after {}s", timeoutSeconds);
        }
    }
}
