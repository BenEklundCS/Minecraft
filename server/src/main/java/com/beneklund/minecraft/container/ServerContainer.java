package com.beneklund.minecraft.container;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static com.beneklund.minecraft.util.Log.PLAYER;
import static com.beneklund.minecraft.util.Log.WORLD;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ChunkStore;
import com.beneklund.minecraft.infra.PlayerStore;
import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.net.GameServer;
import com.beneklund.minecraft.net.IClientLink;
import com.beneklund.minecraft.net.RadiusChunkStreamer;
import com.beneklund.minecraft.player.*;
import com.beneklund.minecraft.world.ServerWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.chunk.LightEngine;
import com.beneklund.minecraft.world.gen.IGenerationSpec;
import com.beneklund.minecraft.world.gen.WorldGenerator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The server's composition root: builds the world, generator, chunk manager and {@link GameServer},
 * and runs the tick thread.
 *
 * <p>The server touches no GL, so the whole object graph is built in the constructor. Clients
 * reach it only through {@link IClientLink}s passed to {@link #accept}; nothing it builds is handed
 * out.
 */
public class ServerContainer {
    private static final int TICKS_PER_SECOND = 20;
    private static final long NANOS_PER_TICK = 1_000_000_000L / TICKS_PER_SECOND;

    private final ServerConfig cfg;
    private final BlockRegistry registry;
    private final WorldGenerator worldGen;
    private final ServerChunkManager chunks;
    private final GameServer server;

    private Thread tickThread;
    // A flag rather than interrupt(): an interrupt cancels whatever file write the tick is in.
    private volatile boolean running;

    public ServerContainer(ServerConfig cfg) {
        this.cfg = cfg;
        World world = new World(new ConcurrentHashMap<>());
        registry = BlockRegistry.createDefault();
        ServerWorldAuthority authority = new ServerWorldAuthority(world, registry, new LightEngine(registry));
        List<IGenerationSpec> generationSpecs = IGenerationSpec.DEFAULT_WORLD_GENERATION;
        worldGen = new WorldGenerator(registry, generationSpecs);
        chunks = new ServerChunkManager(
                new WorldConfig(cfg.seed(), cfg.loadRadius()), world, worldGen, new ChunkStore(cfg.seed()));
        IPlayerStore playerStore = new PlayerStore(cfg.seed());
        server = new GameServer(
                world,
                authority,
                chunks,
                new RadiusChunkStreamer(cfg.loadRadius()),
                playerStore,
                spawn(playerStore),
                new PlayerMovement(new Physics(), MovementTuning.DEFAULT),
                cfg.seed());
        WORLD.debug("server world ready: {} generation spec(s), seed {}", generationSpecs.size(), cfg.seed());
    }

    /**
     * Connects a client. Call before {@link #start()}, because {@link GameServer}'s player list is
     * owned by the tick thread and isn't thread-safe.
     *
     * @throws IllegalStateException if the tick thread has started
     */
    public void accept(IClientLink client) {
        if (tickThread != null) throw new IllegalStateException("accept() after start()");
        server.accept(client);
    }

    /** Starts the {@code server-tick} daemon thread at 20 Hz. */
    public void start() {
        running = true;
        tickThread = new Thread(this::tickLoop, "server-tick");
        tickThread.setDaemon(true);
        tickThread.start();
        LOGGER.info("server ticking at {} Hz", TICKS_PER_SECOND);
    }

    /**
     * Ticks at a fixed rate against an absolute deadline, so sleep jitter doesn't accumulate. A
     * tick that overruns resets the deadline to now, and the next tick starts immediately with no
     * burst of catch-up ticks.
     */
    private void tickLoop() {
        long nextTick = System.nanoTime();
        try {
            while (running) {
                server.tick();
                nextTick += NANOS_PER_TICK;
                long wait = nextTick - System.nanoTime();
                if (wait > 0) {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                } else {
                    nextTick = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LOGGER.error("server tick thread died", t);
        }
    }

    /**
     * Stops the server and saves everything, in this order: join the tick thread, run one last
     * tick for anything a client sent on its way out, save connected players, drain generation,
     * then flush dirty chunks. Generation drains before the flush because a running generation job
     * can dirty a chunk the flush already saved.
     */
    public void stop() {
        long startedAt = System.nanoTime();
        long timeout = cfg.shutdownTimeoutSeconds();
        running = false;
        try {
            if (tickThread != null) tickThread.join(TimeUnit.SECONDS.toMillis(timeout));
            if (tickThread != null && tickThread.isAlive()) {
                LOGGER.warn("server tick thread still running after {}s", timeout);
            } else {
                server.tick();
                server.saveConnectedPlayers();
            }
            chunks.shutdown(timeout);
        } catch (InterruptedException e) {
            LOGGER.warn("interrupted stopping the server, some chunks may not have flushed");
            Thread.currentThread().interrupt();
        }
        chunks.flushAllDirty();
        LOGGER.info("server stopped in {} ms", (System.nanoTime() - startedAt) / 1_000_000);
    }

    /**
     * The saved player position, or else one block above the surface at the configured spawn
     * column. Spawning at a fixed height in the air used to tunnel the player through the ground.
     */
    private PlayerState spawn(IPlayerStore playerStore) {
        PlayerState saved = playerStore.load().orElse(null);
        PlayerState spawn = saved != null
                ? saved
                : new PlayerState(
                        cfg.spawnX(),
                        surfaceHeight((int) Math.floor(cfg.spawnX()), (int) Math.floor(cfg.spawnZ())) + 1,
                        cfg.spawnZ(),
                        cfg.spawnPitch(),
                        cfg.spawnYaw());
        PLAYER.info(
                "spawn {} at ({}, {}, {})", saved != null ? "restored" : "measured", spawn.x(), spawn.y(), spawn.z());
        return spawn;
    }

    /**
     * The y of the highest solid block in a column, found by generating that column's chunk into a
     * throwaway {@link Chunk}. Bedrock at y=0 means a solid block always exists.
     */
    private int surfaceHeight(int worldX, int worldZ) {
        Chunk chunk = new Chunk();
        worldGen.generate(ChunkPos.containing(worldX, worldZ), cfg.seed(), chunk);
        int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            Block id = chunk.getBlock(localX, y, localZ);
            if (id != Block.AIR && registry.get(id).solid()) return y;
        }
        return 0;
    }
}
