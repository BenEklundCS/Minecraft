package com.beneklund.minecraft;

import static com.beneklund.minecraft.util.Log.*;

import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.debug.FrameStreamServer;
import com.beneklund.minecraft.platform.graphics.ChunkMesh;
import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.ScreenCapture;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Hotbar;
import com.beneklund.minecraft.player.Interaction;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.*;
import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.util.RaycastResult;
import com.beneklund.minecraft.world.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Per-frame update/render loop. Drives player input, chunk streaming, GPU uploads, and rendering.
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
    private final ChunkManager chunkManager;
    private final RenderWorld renderWorld;
    private final Camera camera;
    private final Player player;
    private final Physics physics;
    private final DayNightCycle cycle;
    private final InputHandler inputHandler;
    private final World world;
    private final IWorldAuthority authority;
    private final DeltaTracker delta;
    private final InputMapper mapper;
    private final DebugRenderer debugRenderer;
    private final HudRenderer hudRenderer;

    private int uploadsThisSecond;
    private int deletesThisSecond;
    private boolean screenshotRequested;
    // F6. Draws the sun's depth map into the corner so it can be watched while moving — the
    // only way to tell "the map is wrong" apart from "the sampling is wrong", which look
    // identical in the world.
    // -1 is off; otherwise the cascade being shown. F6 cycles off -> 0 -> 1 -> off, because with
    // cascades "is the map right" is really "is each map right", and they fail differently.
    private int shadowOverlayCascade = -1;

    // Null unless local.properties set framestream.port. Serves the last frame over localhost
    // and accepts camera/time commands back, so a change can be evaluated against the same view
    // twice instead of from memory.
    private final FrameStreamServer frameStream;

    public Game(
            Window window,
            Renderer renderer,
            GlFramebuffer sceneBuffer,
            PostProcessor postProcessor,
            ChunkManager chunkManager,
            RenderWorld renderWorld,
            Camera camera,
            Player player,
            Physics physics,
            DayNightCycle cycle,
            InputHandler inputHandler,
            World world,
            IWorldAuthority authority,
            DeltaTracker delta,
            InputMapper mapper,
            DebugRenderer debugRenderer,
            HudRenderer hudRenderer,
            FrameStreamServer frameStream) {
        this.window = window;
        this.renderer = renderer;
        this.sceneBuffer = sceneBuffer;
        this.postProcessor = postProcessor;
        this.chunkManager = chunkManager;
        this.renderWorld = renderWorld;
        this.camera = camera;
        this.player = player;
        this.physics = physics;
        this.cycle = cycle;
        this.inputHandler = inputHandler;
        this.world = world;
        this.authority = authority;
        this.delta = delta;
        this.mapper = mapper;
        this.debugRenderer = debugRenderer;
        this.hudRenderer = hudRenderer;
        this.frameStream = frameStream;
        if (frameStream != null) {
            frameStream.setTeleportHandler(this::applyTeleport);
            frameStream.setTimeHandler(cycle::setTimeOfDay);
        }
    }

    /*
     * pose is {x, y, z, yaw, pitch}; NaN means "leave alone", so a caller can nudge one axis.
     * Runs on the main thread via drainCommands — Player and Camera are not thread-safe and the
     * HTTP threads must never touch them.
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

    public void run() {
        while (!window.shouldClose()) {
            processTitle();
            processInput();
            processPhysics();
            if (frameStream != null) frameStream.drainCommands();
            processChunks();
            cycle.advance(delta.getDelta());
            Hotbar hotbar = player.getHotbar();
            hudRenderer.setHotbar(hotbar.snapshot(), hotbar.selected());
            pushRenderVariables();
            window.beginFrame();
            // drawScene binds the shadow map first and the scene target second — passing the
            // target in rather than binding it here keeps that ordering in one place.
            renderer.drawScene(camera, sceneBuffer);

            // draw() owns the return to the default framebuffer now — it runs several half-res
            // passes first, so it has to do its own binding between them.
            //
            // depthTexture() only answers because sceneBuffer is built with DepthMode.TEXTURE; if
            // this throws, the argument to fix is the one in GameContainer, not the one here.
            postProcessor.draw(
                    sceneBuffer.colorTexture(),
                    sceneBuffer.depthTexture(),
                    sunScreenUV(),
                    window.getWidth(),
                    window.getHeight());

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
            renderer.drawHud(camera);

            // After the HUD so the stream shows exactly what is on screen, overlay included.
            // wantsFrame() gates before readPixels, so a declined frame costs nothing.
            if (frameStream != null && frameStream.wantsFrame(System.currentTimeMillis())) {
                int w = window.getWidth();
                int h = window.getHeight();
                frameStream.submit(ScreenCapture.readPixels(w, h), w, h);
            }

            if (screenshotRequested) {
                captureScreenshot();
                screenshotRequested = false;
            }
            window.endFrame();
        }
    }

    private void pushRenderVariables() {
        renderer.setTime((float) window.getTime());
        renderer.setSkyBrightness(cycle.skyBrightness());
        renderer.setSunDirection(cycle.sunDirection());
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

    // ChunkManager inserts an empty chunk into the World before a worker thread fills it,
    // so "present" isn't "collidable". Only run physics once the player's chunk has been
    // generated — otherwise gravity drags the player down through air that's about to
    // become solid ground, leaving them buried.
    private boolean physicsReady() {
        Chunk chunk = world.getChunk(player.getChunkPos());
        if (chunk == null) return false;
        return switch (chunk.getState()) {
            case UNLOADED, QUEUED_GEN, GENERATING -> false;
            default -> true;
        };
    }

    private void processTitle() {
        delta.tick();
        if (delta.timePassed(1.0f)) {
            int fps = delta.getFrames();
            window.setTitle("Minecraft FPS: %d".formatted(fps));
            PERF.debug(
                    "{} fps ({} ms/frame), {} mesh upload(s), {} buffer delete(s)",
                    fps,
                    fps == 0 ? 0 : 1000 / fps,
                    uploadsThisSecond,
                    deletesThisSecond);
            uploadsThisSecond = 0;
            deletesThisSecond = 0;
            delta.reset();
        }
    }

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
        chunkManager.tick(player.getChunkPos());

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

    private void processPhysics() {
        // TODO: physics steps once per frame on the raw delta, so the simulation is only as
        // stable as the frame rate. Collision is discrete — resolveY checks the cells the box
        // lands in, never the ones it passed through — so a long frame (GC pause, the spawn
        // mesh-upload storm) can move the player far enough to skip clean through a floor.
        //
        // Real engines don't scale dt down to hide this, they stop letting the frame rate set
        // the step at all. Fixed timestep: bank the elapsed time in an accumulator, run as many
        // fixed 1/60 sub-steps as the bank affords, carry the remainder into next frame. A 0.25s
        // hitch becomes 15 small correct steps instead of one huge wrong one, and the sim
        // becomes deterministic — same inputs, same steps, regardless of machine. That's
        // Unity's FixedUpdate, Source's tick rate, and Quake before either of them.
        //
        // The other half is swept collision: test the path the box travels, not just where it
        // lands. Unity calls it Continuous collision detection, Box2D calls it a bullet body.
        // Fixed step is the one to do first — it's what buys determinism.
        //
        // Backlog: "Fixed timestep for physics" on the warm-up shelf in docs/BACKLOG.md.
        float dt = delta.getDelta();
        if (physicsReady()) {
            physics.update(player, authority, dt, player.isFlyMode());
        }
        player.syncCamera();
    }

    /*
     * How many meshes to upload this frame, from how long the last one took.
     *
     * A fixed count per frame makes chunk streaming a function of frame rate, which is a coupling
     * nobody asked for: adding the 512-block shadow cascade cost 18 ms a frame, and that alone cut
     * uploads from 300/s to 128/s. Chunks visibly lagged behind the player and a placed block took
     * noticeably longer to appear — a rendering change quietly throttling the world pipeline.
     *
     * Denominating the budget in seconds instead keeps streaming steady while frame time moves.
     * Still bounded at both ends: at least one so it never stalls completely, and capped so a
     * single long frame cannot spend the recovery uploading a hundred meshes and cause the next
     * long frame.
     */
    private int uploadBudget() {
        int budget = Math.round(TARGET_UPLOADS_PER_SECOND * delta.getDelta());
        return Math.clamp(budget, 1, MAX_UPLOADS_PER_FRAME);
    }

    private void processChunks() {
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

        // Free GL buffers for chunks that left the load radius.
        for (var pos : chunkManager.drainUnloadQueue()) {
            RenderWorld.Entry entry = renderWorld.remove(pos);
            if (entry != null) {
                entry.delete();
                deletesThisSecond++;
                RENDER.trace("freed GL buffers for {}", pos);
            }
        }
    }

    /*
     * Where the sun sits on screen, in [0,1] UV, or empty when it is behind the camera.
     *
     * w = 0 marks a direction rather than a position: the sun has no location, only a bearing, and
     * a projection handles the two differently. After the transform, w carries the view-space
     * depth of that bearing, so its sign is the front/behind test.
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
