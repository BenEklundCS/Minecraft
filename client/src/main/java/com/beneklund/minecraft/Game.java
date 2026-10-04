package com.beneklund.minecraft;

import static com.beneklund.minecraft.util.Log.*;

import com.beneklund.minecraft.infra.ClientChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.IServerLink;
import com.beneklund.minecraft.net.PlayerPrediction;
import com.beneklund.minecraft.platform.debug.FrameStreamServer;
import com.beneklund.minecraft.platform.graphics.ChunkMesh;
import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.GpuTimer;
import com.beneklund.minecraft.platform.graphics.ScreenCapture;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Hotbar;
import com.beneklund.minecraft.player.Interaction;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.player.RaycastResult;
import com.beneklund.minecraft.renderer.RenderPass;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.renderer.camera.Camera;
import com.beneklund.minecraft.renderer.camera.ShadowCamera;
import com.beneklund.minecraft.renderer.chunk.ChunkMeshData;
import com.beneklund.minecraft.renderer.chunk.ChunkRenderer;
import com.beneklund.minecraft.renderer.overlay.DebugRenderer;
import com.beneklund.minecraft.renderer.overlay.HudRenderer;
import com.beneklund.minecraft.renderer.post.PostProcessor;
import com.beneklund.minecraft.util.*;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.chunk.ChunkState;
import com.beneklund.minecraft.world.sky.DayNightCycle;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The client's frame loop, on the main thread: input, physics, server packets and chunk uploads,
 * then the scene, post-processing, HUD and swap.
 *
 * <p>Physics steps at a fixed rate through {@link FixedTimestep} and only once the player's chunk
 * has arrived, since an absent chunk reads as air. Everything else runs once per rendered frame.
 * Each stage is bracketed by an {@link EngineStats} CPU phase, and optionally by a {@link
 * GpuTimer} pass, so the debug server's {@code /stats} can break a frame down.
 *
 * <p>The client joins before the loop starts and learns its spawn from {@code Join.Accepted}.
 * From then on it sends one {@code PlayerInput} per physics step; the server steps its own copy
 * of the player from those and streams chunks around where that copy is.
 */
public class Game {

    /*
     * Mesh uploads per second, and the ceiling on any one frame. 300/s is what the old fixed
     * 4-per-frame delivered at 75 fps, so this preserves the behaviour that was tuned and only
     * removes its dependence on frame rate. The cap is what 4-per-frame gave at 30 fps, which is
     * about as much GL upload as one frame should carry.
     */
    private static final float TARGET_UPLOADS_PER_SECOND = 300.0f;

    private static final int MAX_UPLOADS_PER_FRAME = 8;

    private final Window window;
    private final Renderer renderer;
    private final GlFramebuffer sceneBuffer;
    private final PostProcessor postProcessor;
    private final ClientChunkManager chunkManager;
    private final RenderWorld renderWorld;
    private final Camera camera;
    private final Player player;
    private final PlayerPrediction prediction;
    private final DayNightCycle cycle;
    private final InputHandler inputHandler;
    private final IServerLink serverLink;
    private final IWorldAuthority authority;
    private final DeltaTracker delta;
    private final FixedTimestep timestep;
    private final InputMapper mapper;
    private final DebugRenderer debugRenderer;
    private final HudRenderer hudRenderer;
    private final ChunkRenderer chunkRenderer;
    private final FrameLog frameLog;

    // Nothing is reported until the server has placed the player.
    private boolean joined;
    private int uploadsThisSecond;
    private int deletesThisSecond;
    private boolean screenshotRequested;
    // F6. Draws the sun's depth map into the corner so it can be watched while moving — the
    // only way to tell "the map is wrong" apart from "the sampling is wrong", which look
    // identical in the world.
    // -1 is off; otherwise the cascade being shown. F6 cycles off -> 0 -> 1 -> off, because with
    // cascades "is the map right" is really "is each map right", and they fail differently.
    private int shadowOverlayCascade = -1;

    // Null unless local.properties set debugserver.enabled. Serves the last frame over localhost
    // and accepts camera/time commands back, so a change can be evaluated against the same view
    // twice instead of from memory.
    private final FrameStreamServer frameStream;

    // Null unless gputimer.enabled. Game owns the frame ordinal because it owns the loop, and
    // brackets the post pass because postProcessor.draw is called from here, not from Renderer.
    private final GpuTimer gpuTimer;
    private long frame;

    /**
     * @param frameStream the debug server, or {@code null} when it is off
     * @param gpuTimer per-pass GPU timers, or {@code null} when {@code gputimer.enabled} is unset
     */
    public Game(
            Window window,
            Renderer renderer,
            GlFramebuffer sceneBuffer,
            PostProcessor postProcessor,
            ClientChunkManager chunkManager,
            RenderWorld renderWorld,
            Camera camera,
            Player player,
            PlayerPrediction prediction,
            DayNightCycle cycle,
            InputHandler inputHandler,
            IServerLink serverLink,
            IWorldAuthority authority,
            DeltaTracker delta,
            FixedTimestep timestep,
            InputMapper mapper,
            DebugRenderer debugRenderer,
            HudRenderer hudRenderer,
            ChunkRenderer chunkRenderer,
            FrameLog frameLog,
            FrameStreamServer frameStream,
            GpuTimer gpuTimer) {
        this.window = window;
        this.renderer = renderer;
        this.sceneBuffer = sceneBuffer;
        this.postProcessor = postProcessor;
        this.chunkManager = chunkManager;
        this.renderWorld = renderWorld;
        this.camera = camera;
        this.player = player;
        this.prediction = prediction;
        this.cycle = cycle;
        this.inputHandler = inputHandler;
        this.serverLink = serverLink;
        this.authority = authority;
        this.delta = delta;
        this.timestep = timestep;
        this.mapper = mapper;
        this.debugRenderer = debugRenderer;
        this.chunkRenderer = chunkRenderer;
        this.hudRenderer = hudRenderer;
        this.frameLog = frameLog;
        this.frameStream = frameStream;
        this.gpuTimer = gpuTimer;
        if (frameStream != null) {
            frameStream.setTeleportHandler(this::applyTeleport);
            frameStream.setTimeHandler(cycle::setTimeOfDay);
            frameStream.setStatsHandler(() -> statsSnapshot);
            // Queued by /bench and drained on the main thread, so the log is cleared by the
            // same thread that writes it.
            frameStream.setResetHandler(frameLog::reset);
        }
    }

    /**
     * The {@code /stats} response body as JSON, rebuilt once a second in {@code processTitle} and
     * read from an HTTP thread.
     *
     * <p>Volatile, and built on the main thread, because {@code frameLog}, {@code renderWorld} and
     * {@link EngineStats} are main-thread-only; reading them from the pool thread would be a data
     * race, and it would put {@code percentile()}'s sort back on a thread kept free of it. The
     * debug page parses {@code key=value} lines or JSON, so either shape works.
     */
    private volatile String statsSnapshot = null;

    /**
     * Frame-time percentiles, per-pass draw and vertex counts, GPU pass times and CPU phase times
     * for the last finished frame. GPU fields are {@code -1} when there is no measurement.
     */
    private String formatStats() {
        int renderWorldEntries = renderWorld.getEntries().size();
        JsonObject stats = new JsonObject();
        stats.addProperty("renderWorldEntries", renderWorldEntries);
        stats.addProperty("p50", frameLog.percentile(FrameLog.P50));
        stats.addProperty("p99", frameLog.percentile(FrameLog.P99));
        stats.addProperty("max", frameLog.max());
        stats.addProperty("count", frameLog.count());
        stats.addProperty("opaqueDrawCalls", EngineStats.drawCalls(RenderPass.OPAQUE));
        stats.addProperty("transparentDrawCalls", EngineStats.drawCalls(RenderPass.TRANSPARENT));
        stats.addProperty("shadowDrawCalls", EngineStats.drawCalls(RenderPass.SHADOW));
        stats.addProperty("hudDrawCalls", EngineStats.drawCalls(RenderPass.HUD));
        stats.addProperty("opaqueVertices", EngineStats.vertices(RenderPass.OPAQUE));
        stats.addProperty("transparentVertices", EngineStats.vertices(RenderPass.TRANSPARENT));
        stats.addProperty("shadowVertices", EngineStats.vertices(RenderPass.SHADOW));
        stats.addProperty("hudVertices", EngineStats.vertices(RenderPass.HUD));
        stats.addProperty("uniformUploads", EngineStats.uniformUploads());
        stats.addProperty("chunksConsidered", EngineStats.chunksConsidered());
        stats.addProperty("chunksDrawn", EngineStats.chunksDrawn());

        // -1 all the way through means "no number", never zero: a pass that was skipped and a
        // pass that ran instantly must not look the same, or Stage 4's triage reads backwards.
        // drawShadowPass skips entirely when nothing changed, so this is a normal answer, not
        // an error.
        float shadowC0 = gpuPassMillis(Renderer.TIMER_SHADOW_C0);
        float shadowC1 = gpuPassMillis(Renderer.TIMER_SHADOW_C1);
        float shadowC2 = gpuPassMillis(Renderer.TIMER_SHADOW_C2);
        stats.addProperty("gpuShadowC0Ms", shadowC0);
        stats.addProperty("gpuShadowC1Ms", shadowC1);
        stats.addProperty("gpuShadowC2Ms", shadowC2);
        stats.addProperty("gpuShadowMs", sumOfPassesThatRan(shadowC0, shadowC1, shadowC2));
        stats.addProperty("gpuCloudsMs", gpuPassMillis(Renderer.TIMER_CLOUDS));
        stats.addProperty("gpuOpaqueMs", gpuPassMillis(Renderer.TIMER_OPAQUE));
        stats.addProperty("gpuTransparentMs", gpuPassMillis(Renderer.TIMER_TRANSPARENT));
        stats.addProperty("gpuPostMs", gpuPassMillis(Renderer.TIMER_POST));

        /*
         * The main thread's own frame, region by region. Named cpu* so they sort beside the gpu*
         * pair they are meant to be read against: when p50 is far above the gpu total, the
         * difference is in here.
         *
         * cpuAccountedMs is the sum, and it is reported rather than left to be added up because
         * the useful check is against p50 - a large shortfall means time is going somewhere no
         * region covers, and that is a different bug from any one region being slow.
         */
        float accounted = 0.0f;
        for (CpuPhase phase : CpuPhase.values()) {
            float ms = EngineStats.phaseMillis(phase);
            stats.addProperty("cpu" + name(phase) + "Ms", ms);
            accounted += ms;
        }
        stats.addProperty("cpuAccountedMs", accounted);
        return stats.toString();
    }

    /** {@code INPUT} to {@code Input}, so the JSON key reads {@code cpuInputMs}. */
    private static String name(CpuPhase phase) {
        String n = phase.name();
        return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Sum of the non-negative values, or {@code -1} when none ran. */
    private static float sumOfPassesThatRan(float... millis) {
        float total = -1.0f;
        for (float ms : millis) {
            if (ms < 0.0f) continue;
            total = (total < 0.0f ? 0.0f : total) + ms;
        }
        return total;
    }

    /** A pass's GPU time in milliseconds, or {@code -1} with timers off or no result yet. */
    private float gpuPassMillis(int pass) {
        if (gpuTimer == null) return -1.0f;
        long nanos = gpuTimer.lastResultNanos(pass, frame);
        return nanos < 0 ? -1.0f : nanos / 1_000_000.0f;
    }

    /**
     * Moves the player for the debug server's teleport command. Runs on the main thread from
     * {@code drainCommands}, because {@link Player} and {@link Camera} are not thread-safe.
     *
     * @param pose {@code {x, y, z, yaw, pitch}}; {@code NaN} leaves that component unchanged
     */
    private void applyTeleport(float[] pose) {
        Vector3f p = new Vector3f(player.getPosition());
        if (!Float.isNaN(pose[0])) p.x = pose[0];
        if (!Float.isNaN(pose[1])) p.y = pose[1];
        if (!Float.isNaN(pose[2])) p.z = pose[2];
        player.setPosition(p);
        if (!Float.isNaN(pose[3]) || !Float.isNaN(pose[4])) {
            float yaw = Float.isNaN(pose[3]) ? player.getYaw() : pose[3];
            float pitch = Float.isNaN(pose[4]) ? player.getPitch() : pose[4];
            player.setOrientation(pitch, yaw);
        }
        LOGGER.info("teleport to {} yaw/pitch {}/{}", p, pose[3], pose[4]);
    }

    /** Runs frames until the window is asked to close. */
    public void run() {
        while (!window.shouldClose()) {
            // Closes the previous frame's counters before anything can add to the next one, so
            // everything reported below describes one whole frame rather than a partial one.
            EngineStats.beginFrame();
            frame++;
            processTitle();
            EngineStats.beginPhase(CpuPhase.INPUT);
            processInput();
            EngineStats.endPhase(CpuPhase.INPUT);

            EngineStats.beginPhase(CpuPhase.PHYSICS);
            processPhysics();
            EngineStats.endPhase(CpuPhase.PHYSICS);

            if (frameStream != null) frameStream.drainCommands();

            // Packets from the server, chunk uploads, and whatever ClientChunkManager does on this
            // thread. Standing still this is
            // near zero and flying it is not, which is the difference the whole instrument exists
            // to size.
            EngineStats.beginPhase(CpuPhase.CHUNKS);
            processChunks();
            EngineStats.endPhase(CpuPhase.CHUNKS);

            cycle.advance(delta.getDelta());
            Hotbar hotbar = player.getHotbar();
            hudRenderer.setHotbar(hotbar.snapshot(), hotbar.selected());
            pushRenderVariables();
            window.beginFrame();
            // drawScene binds the shadow map first and the scene target second — passing the
            // target in rather than binding it here keeps that ordering in one place.
            EngineStats.beginPhase(CpuPhase.SCENE);
            renderer.drawScene(camera, sceneBuffer);
            EngineStats.endPhase(CpuPhase.SCENE);

            // draw() owns the return to the default framebuffer now — it runs several half-res
            // passes first, so it has to do its own binding between them.
            //
            // depthTexture() only answers because sceneBuffer is built with DepthMode.TEXTURE; if
            // this throws, the argument to fix is the one in ClientContainer, not the one here.
            EngineStats.beginPhase(CpuPhase.POST);
            if (gpuTimer != null) gpuTimer.begin(Renderer.TIMER_POST, frame);
            postProcessor.draw(
                    sceneBuffer.colorTexture(),
                    sceneBuffer.depthTexture(),
                    sunScreenUV(),
                    window.getWidth(),
                    window.getHeight());
            if (gpuTimer != null) gpuTimer.end(Renderer.TIMER_POST);
            EngineStats.endPhase(CpuPhase.POST);

            // After the tonemap and before the HUD: an instrument drawn over the finished frame,
            // deliberately not part of the image it is being used to debug.
            if (shadowOverlayCascade >= 0) {
                postProcessor.drawDepthOverlay(
                        renderer.shadowMapTexture(),
                        shadowOverlayCascade,
                        renderer.shadowDepthWindowMin(),
                        renderer.shadowDepthWindowMax(),
                        window.getWidth(),
                        window.getHeight());
            }

            // After the tonemap, straight to the window. HUD colours are display values already.
            EngineStats.beginPhase(CpuPhase.HUD);
            renderer.drawHud(camera);
            EngineStats.endPhase(CpuPhase.HUD);

            // After the HUD so the stream shows exactly what is on screen, overlay included.
            // wantsFrame() gates before readPixels, so a declined frame costs nothing.
            EngineStats.beginPhase(CpuPhase.CAPTURE);
            if (frameStream != null && frameStream.wantsFrame(System.currentTimeMillis())) {
                int w = window.getWidth();
                int h = window.getHeight();
                frameStream.submit(ScreenCapture.readPixels(w, h), w, h);
            }
            EngineStats.endPhase(CpuPhase.CAPTURE);

            if (screenshotRequested) {
                captureScreenshot();
                screenshotRequested = false;
            }
            EngineStats.beginPhase(CpuPhase.SWAP);
            window.endFrame();
            EngineStats.endPhase(CpuPhase.SWAP);
        }
    }

    /** Hands this frame's time, frame number and sun state to the renderers before drawing. */
    private void pushRenderVariables() {
        renderer.setTime((float) window.getTime());
        renderer.setFrame(frame);
        renderer.setSkyBrightness(cycle.skyBrightness());
        renderer.setSunDirection(cycle.sunDirection());
        // Same vector, same frame: ChunkRenderer's caster test and the Renderer's light matrix
        // must be built from one reading of the sun, or a chunk gets culled against a sun the
        // shadow map was not rendered from.
        chunkRenderer.setSunDirection(cycle.sunDirection());
        window.setClearColor(renderer.fogColor());
    }

    private void captureScreenshot() {
        int w = window.getWidth();
        int h = window.getHeight();
        ByteBuffer px = ScreenCapture.readPixels(w, h);
        try {
            ScreenCapture.write(px, w, h, Path.of(ScreenCapture.SCREENSHOT_DIR));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Whether the player's chunk has arrived. Until it does it reads as air, and gravity would drop
     * the player through it.
     */
    private boolean physicsReady() {
        return chunkManager.hasBlocks(player.getChunkPos());
    }

    /**
     * Ticks the frame clock and records the frame time. Once a second, updates the window title,
     * logs the {@code perf} summary and rebuilds the {@code /stats} snapshot.
     */
    private void processTitle() {
        delta.tick();
        // Every frame, not once a second: the line below is a summary of what this collects,
        // and a sample taken once a second cannot show a hitch that lasted one frame.
        frameLog.record(delta.getDelta() * 1000.0f);
        if (delta.timePassed(1.0f)) {
            int fps = delta.getFrames();
            window.setTitle("Minecraft FPS: %d".formatted(fps));
            // p50/p99/max rather than 1000/fps: the mean is a summary of the frames you did
            // not notice. The counters beside them are the last complete frame, not a total for
            // the second, so they can be checked straight against the per-frame predictions.
            PERF.debug(
                    "{} fps  p50 {} p99 {} max {} ms ({} samples) | draws {}o {}t {}s {}h,"
                            + " {} uniforms, chunks {}/{} | {} mesh upload(s), {} buffer delete(s)",
                    fps,
                    "%.2f".formatted(frameLog.percentile(FrameLog.P50)),
                    "%.2f".formatted(frameLog.percentile(FrameLog.P99)),
                    "%.2f".formatted(frameLog.max()),
                    frameLog.count(),
                    EngineStats.drawCalls(RenderPass.OPAQUE),
                    EngineStats.drawCalls(RenderPass.TRANSPARENT),
                    EngineStats.drawCalls(RenderPass.SHADOW),
                    EngineStats.drawCalls(RenderPass.HUD),
                    EngineStats.uniformUploads(),
                    EngineStats.chunksDrawn(),
                    EngineStats.chunksConsidered(),
                    uploadsThisSecond,
                    deletesThisSecond);
            // Built here, on the main thread, and only read by the /stats handler. This is the
            // one place that touches frameLog, renderWorld and EngineStats together.
            statsSnapshot = formatStats();
            uploadsThisSecond = 0;
            deletesThisSecond = 0;
            delta.reset();
        }
    }

    /**
     * Polls GLFW, maps events to actions, handles the loop-level ones (exit, screenshot, shadow
     * overlay, shader reload), then hands the rest to the player.
     */
    private void processInput() {
        window.pollEvents();
        List<IInputAction> actions = mapper.drain(delta.getDelta());

        if (actions.contains(IInputAction.Simple.EXIT)) {
            window.close();
        }

        if (actions.contains(IInputAction.Simple.SCREENSHOT)) {
            screenshotRequested = true;
        }

        if (actions.contains(IInputAction.Simple.DEBUG_SHADOW_MAP)) {
            shadowOverlayCascade =
                    shadowOverlayCascade + 1 >= ShadowCamera.cascadeCount() ? -1 : shadowOverlayCascade + 1;
            RENDER.info("shadow map overlay {}", shadowOverlayCascade < 0 ? "off" : "cascade " + shadowOverlayCascade);
        }

        if (actions.contains(IInputAction.Simple.RELOAD_SHADERS)) {
            renderer.reloadAll();
            postProcessor.reload();
        }

        inputHandler.handle(actions);
        chunkManager.tick();

        List<Interaction> interactions = player.tick(actions);
        for (Interaction interaction : interactions) {
            if (interaction
                    instanceof
                    Interaction.BlockInteraction(boolean broken, Vector3f eye, Vector3f dir, RaycastResult result)) {
                if (broken) {
                    debugRenderer.updateFromRaycast(eye, dir, result);
                }
            }
        }

        debugRenderer.updateTargetedBlock(player.getTargetedBlock());
    }

    /**
     * Runs however many fixed physics steps this frame's time covers, sending one input per step,
     * then syncs the camera.
     */
    private void processPhysics() {
        float dt = delta.getDelta();
        int steps = timestep.stepsFor(dt);
        // prediction.step both moves the player and returns the input for that step, so a
        // predicted step and an input on the wire can't come apart; replay depends on that.
        if (joined && physicsReady()) {
            PlayerIntent intent = player.intent();
            for (int i = 0; i < steps; i++) {
                serverLink.send(prediction.step(player, authority, intent));
            }
        }
        player.syncCamera();
    }

    /** Moves the player to the spawn the server chose and starts sending inputs. */
    private void onJoined(IPacket.Join.Accepted accepted) {
        PlayerState spawn = accepted.spawn();
        player.setPosition(new Vector3f(spawn.x(), spawn.y(), spawn.z()));
        player.setOrientation(spawn.pitch(), spawn.yaw());
        joined = true;
        prediction.reset();
        LOGGER.info("joined as player {} at server tick {}", accepted.playerId(), accepted.serverTick());
    }

    /**
     * How many meshes to upload this frame: {@code TARGET_UPLOADS_PER_SECOND} times the last
     * frame's duration, clamped to [1, {@code MAX_UPLOADS_PER_FRAME}].
     *
     * <p>A budget in seconds keeps chunk streaming steady as frame time moves. A fixed count per
     * frame tied streaming to frame rate: adding the 512-block shadow cascade cost 18 ms a frame and
     * cut uploads from 300/s to 128/s, so chunks lagged behind the player and placed blocks appeared
     * late. The floor of one keeps streaming alive in a slow frame; the cap stops one long frame from
     * spending its recovery on a hundred uploads and causing the next long frame.
     */
    private int uploadBudget() {
        int budget = Math.round(TARGET_UPLOADS_PER_SECOND * delta.getDelta());
        return Math.clamp(budget, 1, MAX_UPLOADS_PER_FRAME);
    }

    /**
     * Drains the server link and routes each packet: chunk packets to the replica, join replies to
     * {@link #onJoined}. Other packet types are ignored.
     */
    private void receivePackets() {
        for (IPacket.ToClient packet : serverLink.drain()) {
            switch (packet) {
                case IPacket.ToClient.ChunkData data -> chunkManager.onChunkData(data);
                case IPacket.ToClient.ChunkUnload unload -> chunkManager.onChunkUnload(unload);
                case IPacket.ToClient.BlockChanged changed -> chunkManager.onBlockChanged(changed);
                case IPacket.ToClient.PlayerUpdate update -> reconcile(update);
                case IPacket.Join.Accepted accepted -> onJoined(accepted);
                case IPacket.Join.Rejected rejected -> LOGGER.warn("server refused the join: {}", rejected.reason());
                default -> {}
            }
        }
    }

    /**
     * Adopts the server's view of the player and replays what it hasn't acked yet. When the
     * prediction was right the body ends where it started, so the traced distance should read 0.0
     * almost every time. Breaking the block underfoot is the usual exception: the server applies
     * the edit a tick before the replica hears about it.
     *
     * <p>This runs after {@code processPhysics} has synced the camera, so a correction reaches the
     * screen a frame late. At 0 that's invisible; if a one-frame pop ever shows, sync again here.
     */
    private void reconcile(IPacket.ToClient.PlayerUpdate update) {
        Vector3f before = new Vector3f(player.getPosition());
        prediction.reconcile(player, authority, update);
        PLAYER.trace(
                "reconciled ack {} pending {} correction {}",
                update.ackTick(),
                prediction.pending(),
                before.distance(player.getPosition()));
    }

    private void processChunks() {
        receivePackets();

        // Upload as many new meshes as this frame's budget allows — ChunkMesh asserts main thread.
        // Skip empty buffers so chunks with no opaque (or no transparent) geometry don't
        // allocate a zero-length VAO; null means "nothing to draw for this pass".
        for (ChunkMeshData data : chunkManager.drainUploadQueue(uploadBudget())) {
            ChunkMesh opaque = data.opaque().isEmpty() ? null : new ChunkMesh(data.opaque());
            ChunkMesh transparent = data.transparent().isEmpty() ? null : new ChunkMesh(data.transparent());
            renderWorld.add(data.pos(), opaque, transparent);
            data.chunk().tryTransition(ChunkState.UPLOADED);
            uploadsThisSecond++;
            RENDER.trace("uploaded {} (opaque={}, transparent={})", data.pos(), opaque != null, transparent != null);
        }

        for (var pos : chunkManager.drainUnloadQueue()) {
            RenderWorld.Entry entry = renderWorld.remove(pos);
            if (entry != null) {
                entry.delete();
                deletesThisSecond++;
                RENDER.trace("freed GL buffers for {}", pos);
            }
        }
    }

    /**
     * Where the sun sits on screen in [0, 1] UV, for the light shafts, or empty when it is behind
     * the camera.
     *
     * <p>The sun is a bearing with no location, so it is transformed as a direction, {@code w = 0},
     * which drops the view translation. After projection {@code w} holds the view-space depth of
     * that bearing, so its sign is the front/behind test.
     *
     * @see <a href="https://www.songho.ca/opengl/gl_projectionmatrix.html">Song Ho Ahn: OpenGL
     *     Projection Matrix</a>
     */
    private Optional<Vector2f> sunScreenUV() {
        Vector3f sun = cycle.sunDirection();
        Vector4f clip = camera.getViewProjectionMatrix().transform(new Vector4f(sun.x, sun.y, sun.z, 0.0f));

        // Behind the camera, w is negative and the divide mirrors the result onto the screen —
        // the effect would then radiate from a phantom sun in the wrong place.
        if (clip.w <= 0.0f) return Optional.empty();

        return Optional.of(
                new Vector2f(clip.x / clip.w, clip.y / clip.w).mul(0.5f).add(0.5f, 0.5f));
    }
}
