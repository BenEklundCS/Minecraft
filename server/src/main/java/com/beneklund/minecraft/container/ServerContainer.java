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
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.LightEngine;
import com.beneklund.minecraft.world.ServerWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.gen.IGenerationSpec;
import com.beneklund.minecraft.world.gen.WorldGenerator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Server composition root. No GL, so the whole graph is built in the constructor. Clients reach it
// only through links; nothing it builds is handed out.
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
                cfg.seed());
        WORLD.debug("server world ready: {} generation spec(s), seed {}", generationSpecs.size(), cfg.seed());
    }

    // Before start(): GameServer's player list isn't thread-safe.
    public void accept(IClientLink client) {
        if (tickThread != null) throw new IllegalStateException("accept() after start()");
        server.accept(client);
    }

    public void start() {
        running = true;
        tickThread = new Thread(this::tickLoop, "server-tick");
        tickThread.setDaemon(true);
        tickThread.start();
        LOGGER.info("server ticking at {} Hz", TICKS_PER_SECOND);
    }

    // Fixed rate; an overrun starts the next tick immediately rather than bursting to catch up.
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

    // Tick thread, one last tick for anything a client sent on its way out, players, generation,
    // then chunks — a running generate() can dirty a saved chunk.
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

    // Saved position, or standing on the surface at the configured column — spawning in the air
    // used to tunnel the player through the ground.
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

    // Highest solid block in a column. Bedrock at y=0 means it always finds one.
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
