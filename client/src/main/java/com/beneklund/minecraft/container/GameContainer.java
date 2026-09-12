package com.beneklund.minecraft.container;

import static com.beneklund.minecraft.util.Log.AUDIO;
import static com.beneklund.minecraft.util.Log.LOGGER;
import static com.beneklund.minecraft.util.Log.WORLD;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.*;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.IServerLink;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.audio.StbAudioLoader;
import com.beneklund.minecraft.platform.debug.FrameStreamServer;
import com.beneklund.minecraft.platform.graphics.*;
import com.beneklund.minecraft.platform.images.StbImageLoader;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.resources.JsonResourcePack;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.*;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.util.FixedTimestep;
import com.beneklund.minecraft.util.FrameLog;
import com.beneklund.minecraft.world.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final int SHADOW_MAP_SIZE = 2048;

    // How far down from the window the cloud march runs, on each axis. 4 is the knob to turn if
    // clouds cost too much (8) or the edge where one crosses the sun disc looks too soft (2).
    private static final int CLOUD_BUFFER_DIVISOR = 3;

    private final ContainerConfig cfg;

    // config
    private LocalConfig localConfig;
    private WindowConfig windowConfig;
    private CameraConfig cameraConfig;

    // input
    private InputEventQueue inputEventQueue;
    private InputMapper inputMapper;
    private InputHandler inputHandler;

    // platform
    private Window window;
    private Camera camera;
    private DeltaTracker delta;
    private FixedTimestep timestep;
    private FrameLog frameLog;

    // renderer
    private TextureAtlas atlas;
    private BlockRegistry registry;
    private RenderWorld renderWorld;
    private DebugRenderer debugRenderer;
    private HudRenderer hudRenderer;
    // Held rather than local: Game pushes the sun to it each frame, the same way it does the
    // Renderer, so the caster test and the light matrix cannot disagree within a frame.
    private ChunkRenderer chunkRenderer;
    private Renderer renderer;
    private GlFramebuffer sceneBuffer;
    private ShadowFramebuffer shadowBuffer;
    private ShadowCamera shadowCamera;
    private FrameStreamServer frameStream;
    private GpuTimer gpuTimer;
    private GlFramebuffer bloomA;
    private GlFramebuffer bloomB;
    private GlFramebuffer godrayA;
    private GlFramebuffer godrayB;
    private GlFramebuffer cloudBuffer;
    private CloudRenderer cloudRenderer;
    private PostProcessor postProcessor;

    // Which optional passes this run draws. Read from local.properties in initConfig, used by
    // initRenderer — the only two consumers are Renderer and PostProcessor, and both take it in
    // their constructor rather than being told to change mid-run.
    private RenderFeatures renderFeatures;

    // audio
    private AudioPlayer music;

    private final IServerLink serverLink;

    // world
    private ClientWorldAuthority authority;
    private ClientChunkManager chunkManager;
    private Physics physics;
    private DayNightCycle cycle;

    // player
    private Player player;

    // Not checked by the server yet.
    private static final String USERNAME = "player";
    private static final int PROTOCOL_VERSION = 1;

    public GameContainer(ContainerConfig cfg, IServerLink serverLink) {
        this.cfg = cfg;
        this.serverLink = serverLink;
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
        initFrameStream();
        // The server only sends chunks to a joined player, connect to the server using serverLink.
        serverLink.send(new IPacket.Join.Request(USERNAME, PROTOCOL_VERSION));
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
        // shaders.simple strips the frame back to terrain and sky. Logged at info rather than
        // debug: it changes the image enough that a screenshot taken with it on and read back
        // later is otherwise a mystery.
        renderFeatures = localConfig.simpleShaders() ? RenderFeatures.simple() : RenderFeatures.full();
        if (localConfig.simpleShaders()) LOGGER.info("simple shaders: {}", renderFeatures);
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
        timestep = new FixedTimestep();
        // Beside delta because they are one concern, not because it needs anything from this
        // phase - FrameLog touches no GL and no window, so it is safe anywhere above buildGame.
        frameLog = new FrameLog(FrameLog.FRAME_HISTORY);
        window.addResizeListener(camera::setWindowSize);
    }

    private void initRenderer() throws IOException {
        JsonResourcePack resourcePack = new JsonResourcePack(cfg.resourcePack(), new StbImageLoader());
        atlas = new TextureAtlas(resourcePack);
        registry = BlockRegistry.createDefault();
        renderWorld = new RenderWorld();
        SkyRenderer skyRenderer = new SkyRenderer();
        chunkRenderer = new ChunkRenderer(renderWorld, atlas, renderFeatures);
        debugRenderer = new DebugRenderer();
        hudRenderer = new HudRenderer(registry, atlas);
        // No initial sky brightness here on purpose — Game.run sets it from the DayNightCycle
        // every frame before draw(), so a value passed in would only ever be the one that
        // never gets used.
        // Fixed 2048 square, not window-sized: shadow map resolution is a quality setting, and
        // at SHADOW_BOX_HALF = 128 this is one texel per eighth of a block.
        shadowBuffer = new ShadowFramebuffer(SHADOW_MAP_SIZE, ShadowCamera.cascadeCount());
        // Same size for both, from one constant: the camera snaps the box to whole texels, so it
        // has to agree with the map it is snapping to or the snapping quietly stops working.
        shadowCamera = new ShadowCamera(SHADOW_MAP_SIZE);
        // Before the Renderer, which takes both: the cloud pass is one of the passes it sequences.
        constructCloudBuffer();
        cloudRenderer = new CloudRenderer();
        renderer = new Renderer(
                List.of(skyRenderer, chunkRenderer, debugRenderer, hudRenderer),
                cfg.clearColor(),
                shadowBuffer,
                shadowCamera,
                cloudRenderer,
                cloudBuffer,
                renderWorld::version,
                renderFeatures);

        // After the Renderer because it needs the pass numbering, and after window.init()
        // because the constructor calls glGenQueries. Absent flag means no timer at all rather
        // than a disabled one - a query per pass per frame is cheap but not free, and Act II
        // wants a clean comparison with it off.
        if (localConfig.gpuTimerEnabled()) {
            gpuTimer = new GpuTimer(Renderer.TIMER_PASS_COUNT);
            renderer.setGpuTimer(gpuTimer);
            LOGGER.info("gpu timers on ({} passes)", Renderer.TIMER_PASS_COUNT);
        }

        // The scene renders here instead of straight to the window, and PostProcessor draws it
        // back out. RGBA16F because sky.frag emits linear radiance now — the sun runs well past
        // 1.0 and an 8-bit attachment would clip it before the tonemap in post.frag sees it.
        //
        // Registered for resize: the attachments are allocated at a fixed size, so without this
        // the scene keeps rendering at the launch resolution and gets stretched.
        //
        // DepthMode.TEXTURE: this is the one framebuffer whose depth anything reads back. World
        // geometry needs a real depth test while it draws, and screen-space passes downstream need
        // to sample the result to find out how far away each pixel ended up.
        sceneBuffer = new GlFramebuffer(window.getWidth(), window.getHeight(), GL_RGBA16F, DepthMode.TEXTURE);

        constructBloomBuffers();
        constructGodrayBuffers();
        postProcessor = new PostProcessor(Renderer.EXPOSURE, bloomA, bloomB, godrayA, godrayB, renderFeatures);
    }

    private void constructBloomBuffers() {
        int bloomW = Math.max(1, window.getWidth() / 2);
        int bloomH = Math.max(1, window.getHeight() / 2);
        // DepthMode.NONE: every pass that touches these draws one screen-covering quad, so there
        // is nothing to sort and nothing to occlude. A depth attachment here would be allocated,
        // reallocated on every resize below, and never written to.
        bloomA = new GlFramebuffer(bloomW, bloomH, GL_RGBA16F, DepthMode.NONE);
        bloomB = new GlFramebuffer(bloomW, bloomH, GL_RGBA16F, DepthMode.NONE);
        window.addResizeListener((w, h) -> {
            bloomA.resize(Math.max(1, w / 2), Math.max(1, h / 2));
            bloomB.resize(Math.max(1, w / 2), Math.max(1, h / 2));
        });
        window.addResizeListener(sceneBuffer::resize);
    }

    /*
     * A quarter of the window on each axis, so a sixteenth of the pixels. The raymarch in
     * cloud.frag costs CLOUD_STEPS view samples and up to CLOUD_LIGHT_STEPS more per lit one, and
     * paying that per screen pixel is the difference between clouds and a slideshow. Clouds are
     * low-frequency enough that the bilinear stretch back up is nearly free visually — the cost
     * shows on the sun disc's edge, not on the clouds themselves.
     *
     * DepthMode.NONE, like the bloom and godray pairs: one fullscreen triangle, nothing to sort.
     * RGBA16F because the alpha channel carries transmittance and the rgb carries linear radiance
     * that runs well past 1.0 wherever the sun catches a rim.
     */
    private void constructCloudBuffer() {
        int cloudW = Math.max(1, window.getWidth() / CLOUD_BUFFER_DIVISOR);
        int cloudH = Math.max(1, window.getHeight() / CLOUD_BUFFER_DIVISOR);
        cloudBuffer = new GlFramebuffer(cloudW, cloudH, GL_RGBA16F, DepthMode.NONE);
        window.addResizeListener((w, h) ->
                cloudBuffer.resize(Math.max(1, w / CLOUD_BUFFER_DIVISOR), Math.max(1, h / CLOUD_BUFFER_DIVISOR)));
    }

    private void constructGodrayBuffers() {
        int godrayW = Math.max(1, window.getWidth() / 2);
        int godrayH = Math.max(1, window.getHeight() / 2);
        // Same story as the bloom pair: fullscreen quads only. The depth these read comes from
        // sceneBuffer, not from a depth buffer of their own.
        godrayA = new GlFramebuffer(godrayW, godrayH, GL_RGBA16F, DepthMode.NONE);
        godrayB = new GlFramebuffer(godrayW, godrayH, GL_RGBA16F, DepthMode.NONE);
        window.addResizeListener((w, h) -> {
            godrayA.resize(Math.max(1, w / 2), Math.max(1, h / 2));
            godrayB.resize(Math.max(1, w / 2), Math.max(1, h / 2));
        });
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

    // A replica filled by the server; generation and saves live in ServerContainer.
    private void initWorld() {
        World world = new World(new ConcurrentHashMap<>());
        LightEngine lightEngine = new LightEngine(registry);
        authority = new ClientWorldAuthority(world, registry, serverLink);
        ChunkMesher mesher = new ChunkMesher(registry, atlas);
        chunkManager = new ClientChunkManager(world, mesher, lightEngine, registry, authority);
        physics = new Physics();
        cycle = new DayNightCycle(DayNightCycle.MORNING, DayNightCycle.VERY_SHORT_DAY_SECONDS);
        WORLD.debug("client world ready, chunks come from the server");
    }

    // Placed at the spawn when Join.Accepted arrives.
    private void initPlayer() {
        player = new Player(cfg.player(), camera, authority);
    }

    /*
     * Opt-in, behind debugserver.enabled: without it no socket is opened and no frame is ever
     * read back.
     *
     * Off is the right default for anything you intend to measure. The server captures the
     * framebuffer every 100 ms with no check for whether a browser is attached, and glReadPixels
     * is a synchronous stall that waits for the whole pipeline to drain. Logged at info when it
     * starts, so a timing run that quietly included it is identifiable afterwards.
     */
    private void initFrameStream() {
        if (!localConfig.debugServerEnabled()) return;
        int port = localConfig.debugServerPort();
        try {
            frameStream = new FrameStreamServer(port);
            frameStream.start();
            LOGGER.info("debug server on http://127.0.0.1:{}/ - captures a frame every 100 ms", port);
        } catch (IOException e) {
            LOGGER.warn("debug server failed to start on port {}", port, e);
            frameStream = null;
        }
    }

    private Game buildGame() {
        return new Game(
                window,
                renderer,
                sceneBuffer,
                postProcessor,
                chunkManager,
                renderWorld,
                camera,
                player,
                physics,
                cycle,
                inputHandler,
                serverLink,
                authority,
                delta,
                timestep,
                inputMapper,
                debugRenderer,
                hudRenderer,
                chunkRenderer,
                frameLog,
                frameStream,
                gpuTimer);
    }

    // Reverse dependency order: audio before window (AL before GLFW/GL).
    // Chunk flushing is ServerContainer.stop's.
    private void shutdown() {
        // First: it holds a socket and a worker thread, and neither depends on anything below.
        if (frameStream != null) frameStream.stop();
        long startedAt = System.nanoTime();
        leaveServer();
        try {
            chunkManager.shutdown(cfg.shutdownTimeoutSeconds());
        } catch (InterruptedException e) {
            LOGGER.warn("interrupted waiting for meshing workers");
            Thread.currentThread().interrupt();
        }
        music.shutdown();
        postProcessor.delete();
        sceneBuffer.delete();
        cloudBuffer.delete();
        bloomB.delete();
        bloomA.delete();
        if (gpuTimer != null) gpuTimer.delete();
        renderer.delete();
        atlas.delete();
        window.shutdown();
        LOGGER.info("shutdown complete in {} ms", millisSince(startedAt));
    }

    // The server saves the player from this last position.
    private void leaveServer() {
        Vector3f p = player.getPosition();
        serverLink.send(new IPacket.ToServer.PlayerPosition(p.x(), p.y(), p.z(), player.getPitch(), player.getYaw()));
        serverLink.send(new IPacket.ToServer.Disconnect("quit"));
    }
}
