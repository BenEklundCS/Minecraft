package com.beneklund.minecraft.container;

import static com.beneklund.minecraft.util.Log.AUDIO;
import static com.beneklund.minecraft.util.Log.LOGGER;
import static com.beneklund.minecraft.util.Log.PLAYER;
import static com.beneklund.minecraft.util.Log.WORLD;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.*;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.audio.StbAudioLoader;
import com.beneklund.minecraft.platform.images.StbImageLoader;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.resources.JsonResourcePack;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.renderer.*;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.gen.IGenerationSpec;
import com.beneklund.minecraft.world.gen.WorldGenerator;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector2f;
import org.joml.Vector3f;

// Composition root — the only place that wires concrete types together.
// Nothing outside this class should call `new` on platform or renderer objects.
//
// The init* methods are grouped by what they're allowed to touch, and run() calls them in
// an order that respects those rules. The fields exist so the groups can hand objects to
// each other — nothing outside run() reads them.
public class GameContainer {
    // Classloader-relative, no leading slash — StbAudioLoader.listOggs resolves it through the
    // context classloader, which rejects an absolute-looking name. Searched recursively, so
    // every album under it is in the pool and none of them is named here: the repo ships one
    // CC0 folder (CREDITS.txt)
    private static final String MUSIC_DIR = "music";

    private final ContainerConfig cfg;

    // config
    private LocalConfig localConfig;
    private WindowConfig windowConfig;
    private CameraConfig cameraConfig;
    private WorldConfig worldConfig;

    // input
    private InputEventQueue inputEventQueue;
    private InputMapper inputMapper;
    private InputHandler inputHandler;

    // platform
    private Window window;
    private Camera camera;
    private DeltaTracker delta;

    // renderer
    private TextureAtlas atlas;
    private BlockRegistry registry;
    private RenderWorld renderWorld;
    private DebugRenderer debugRenderer;
    private HudRenderer hudRenderer;
    private Renderer renderer;

    // audio
    private AudioPlayer music;

    // world
    private World world;
    private LocalWorldAuthority authority;
    private WorldGenerator worldGen;
    private ChunkManager chunkManager;
    private Physics physics;
    private DayNightCycle cycle;

    // player
    private IPlayerStore playerStore;
    private Player player;

    public GameContainer(ContainerConfig cfg) {
        this.cfg = cfg;
    }

    public void run() throws IOException {
        long startedAt = System.nanoTime();
        LOGGER.info("starting up");
        LOGGER.info("max heap: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));

        // 1. Game Config - pure data, no platform deps.
        initConfig();
        phaseDone("config", startedAt);
        // 2. Input plumbing - pure Java, no GLFW/GL yet.
        initInput();
        // 3. Pre-init platform objects - constructed but not yet active.
        initPlatform();
        phaseDone("input+platform", startedAt);

        // 4. window.init() - creates the GLFW window and makes the GL context current.
        //    Nothing that calls GL or uploads to the GPU may run before this line.
        window.init();
        phaseDone("window", startedAt);

        // 5. GL resources - shaders, VAOs, textures. Requires active GL context.
        initRenderer();
        phaseDone("renderer", startedAt);
        // 6. Audio - OpenAL is lazy-initialized on first play(), but construct after GL
        //    so the window is confirmed healthy before we open the audio device.
        initAudio();
        // 7. Game logic - depends on input and the GL renderer being ready.
        initWorld();
        initPlayer();
        phaseDone("world+player", startedAt);

        LOGGER.info("startup complete in {} ms, entering game loop", millisSince(startedAt));

        buildGame().run();

        // 8. Shutdown - see the ordering note on shutdown().
        LOGGER.info("game loop exited, shutting down");
        shutdown();
    }

    // Cumulative rather than per-phase: what you actually want to know is how far into startup
    // you are when something hangs, and cumulative survives phases being reordered.
    private static void phaseDone(String phase, long startedAt) {
        LOGGER.debug("init {} done at {} ms", phase, millisSince(startedAt));
    }

    private static long millisSince(long nanos) {
        return (System.nanoTime() - nanos) / 1_000_000;
    }

    private void initConfig() {
        localConfig = new LocalConfig();
        windowConfig = new WindowConfig(
                cfg.windowTitle(),
                cfg.windowWidth(),
                cfg.windowHeight(),
                cfg.vsync(),
                cfg.mode(),
                cfg.clearColor(),
                localConfig.debugEnabled());
        cameraConfig = new CameraConfig(cfg.fov());
        worldConfig = new WorldConfig(cfg.seed(), cfg.renderDistance());
        LOGGER.info("seed={} renderDistance={} fov={}", cfg.seed(), cfg.renderDistance(), cfg.fov());
    }

    private void initInput() {
        inputEventQueue = new InputEventQueue();
        inputMapper = new InputMapper(inputEventQueue);
    }

    private void initPlatform() {
        camera = new Camera(windowConfig, cameraConfig);
        window = new Window(windowConfig, inputEventQueue);
        inputHandler = new InputHandler(window, camera);
        delta = new DeltaTracker(window::getTime);
        window.addResizeListener(camera::setWindowSize);
    }

    private void initRenderer() throws IOException {
        JsonResourcePack resourcePack = new JsonResourcePack(cfg.resourcePack(), new StbImageLoader());
        atlas = new TextureAtlas(resourcePack);
        registry = BlockRegistry.createDefault();
        renderWorld = new RenderWorld();
        SkyRenderer skyRenderer = new SkyRenderer();
        ChunkRenderer chunkRenderer = new ChunkRenderer(renderWorld, atlas);
        debugRenderer = new DebugRenderer();
        hudRenderer = new HudRenderer(registry, atlas);
        float end = 0.9f * cfg.renderDistance() * Chunk.SIZE_XZ;
        float start = 0.55f * end;
        Vector2f fogRange = new Vector2f(start, end);
        // No initial sky brightness here on purpose — Game.run sets it from the DayNightCycle
        // every frame before draw(), so a value passed in would only ever be the one that
        // never gets used.
        renderer = new Renderer(
                List.of(skyRenderer, chunkRenderer, debugRenderer, hudRenderer), cfg.clearColor(), fogRange);
    }

    private void initAudio() {
        StbAudioLoader loader = new StbAudioLoader();
        music = new AudioPlayer(loader);

        String disc = getLocalConfigAudioPath().orElseGet(() -> getRandomAudioPath(loader));
        AUDIO.info("startup disc: {}", disc);
        music.play(disc);
    }

    private Optional<String> getLocalConfigAudioPath() {
        return localConfig.startupDisc();
    }

    private String getRandomAudioPath(StbAudioLoader loader) {
        List<String> discs = loader.listOggs(MUSIC_DIR).stream()
                .filter(s -> s.contains(localConfig.preferredAlbum().orElse("")))
                .toList();
        if (discs.isEmpty()) throw new IllegalStateException("No .ogg files found under %s".formatted(MUSIC_DIR));
        return discs.get(ThreadLocalRandom.current().nextInt(discs.size()));
    }

    private void initWorld() {
        world = new World(new ConcurrentHashMap<>());
        LightEngine lightEngine = new LightEngine(registry);
        authority = new LocalWorldAuthority(world, registry, lightEngine);
        List<IGenerationSpec> generationSpecs = IGenerationSpec.DEFAULT_WORLD_GENERATION;
        worldGen = new WorldGenerator(registry, generationSpecs);
        ChunkMesher mesher = new ChunkMesher(registry, atlas);
        ChunkStore store = new ChunkStore(worldConfig.seed());
        chunkManager = new ChunkManager(worldConfig, world, worldGen, mesher, authority, store, lightEngine);
        physics = new Physics();
        cycle = new DayNightCycle(DayNightCycle.NOON, DayNightCycle.SHORT_DAY_SECONDS);
        WORLD.debug("world ready: {} generation spec(s), seed {}", generationSpecs.size(), worldConfig.seed());
    }

    private void initPlayer() {
        playerStore = new PlayerStore(worldConfig.seed());
        player = new Player(cfg.player(), camera, authority);

        PlayerState saved = playerStore.load().orElse(null);
        PlayerState spawn = saved != null ? saved : defaultSpawn();
        PLAYER.info(
                "spawn {} at ({}, {}, {})", saved != null ? "restored" : "measured", spawn.x(), spawn.y(), spawn.z());
        player.setPosition(new Vector3f(spawn.x(), spawn.y(), spawn.z()));
        player.setOrientation(spawn.pitch(), spawn.yaw());
    }

    private Game buildGame() {
        return new Game(
                window,
                renderer,
                chunkManager,
                renderWorld,
                camera,
                player,
                physics,
                cycle,
                inputHandler,
                world,
                authority,
                delta,
                inputMapper,
                debugRenderer,
                hudRenderer);
    }

    // Reverse dependency order: audio before window (AL before GLFW/GL).
    // Stop chunk workers before flushing so no async setBlock can dirty a chunk
    // after we've already persisted it.
    private void shutdown() {
        long startedAt = System.nanoTime();
        savePlayerState();
        try {
            chunkManager.shutdown(cfg.shutdownTimeoutSeconds());
        } catch (InterruptedException e) {
            LOGGER.warn("interrupted waiting for chunk workers, some chunks may not have flushed");
            Thread.currentThread().interrupt();
        }
        chunkManager.flushAllDirty();
        music.shutdown();
        renderer.delete();
        atlas.delete();
        window.shutdown();
        LOGGER.info("shutdown complete in {} ms", millisSince(startedAt));
    }

    private void savePlayerState() {
        Vector3f p = player.getPosition();
        float pi = player.getPitch();
        float yaw = player.getYaw();
        playerStore.save(new PlayerState(p.x(), p.y(), p.z(), pi, yaw));
    }

    // Where to put the player when there's no save to load. Only the configured x/z are
    // used — y is measured, not configured.
    //
    // Spawn resting directly on the surface (feet one block above the highest solid).
    // We used to spawn ~12 blocks up and free-fall, but that gave gravity a chance to
    // build speed and tunnel the player through the ground during a spawn-time frame
    // hitch (the mesh/upload storm). No fall = no tunnel. The generator is deterministic,
    // so this measured height matches the async-generated chunk exactly.
    private PlayerState defaultSpawn() {
        PlayerConfig p = cfg.player();
        Vector3f start = p.startPosition();
        int surfaceY = surfaceHeight(
                worldGen, worldConfig.seed(), registry, (int) Math.floor(start.x()), (int) Math.floor(start.z()));
        return new PlayerState(start.x(), surfaceY + 1, start.z(), p.startPitch(), p.startYaw());
    }

    // Highest solid block in a world column. Generates that column's chunk and scans
    // top-down — air, water, and leaves are skipped so we land on real ground. Bedrock at
    // y=0 guarantees the loop always finds something.
    private static int surfaceHeight(
            WorldGenerator worldGen, long seed, BlockRegistry registry, int worldX, int worldZ) {
        Chunk chunk = new Chunk();
        ChunkPos pos = new ChunkPos(Math.floorDiv(worldX, Chunk.SIZE_XZ), Math.floorDiv(worldZ, Chunk.SIZE_XZ));
        worldGen.generate(pos, seed, chunk);

        int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            Block id = chunk.getBlock(localX, y, localZ);
            if (id != Block.AIR && registry.get(id).solid()) return y;
        }
        return 0;
    }
}
