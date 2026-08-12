package com.beneklund.minecraft;

import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.graphics.ChunkMesh;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Interaction;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.*;
import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.util.RaycastResult;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkState;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.World;
import java.util.List;
import org.joml.Vector3f;

// Per-frame update/render loop. Drives player input, chunk streaming, GPU uploads, and rendering.
public class Game {

    private static final int MAX_UPLOADS_PER_FRAME = 4;

    private final Window window;
    private final Renderer renderer;
    private final ChunkManager chunkManager;
    private final RenderWorld renderWorld;
    private final Camera camera;
    private final Player player;
    private final Physics physics;
    private final World world;
    private final IWorldAuthority authority;
    private final DeltaTracker delta;
    private final InputMapper mapper;
    private final DebugRenderer debugRenderer;
    private final HudRenderer hudRenderer;

    public Game(
            Window window,
            Renderer renderer,
            ChunkManager chunkManager,
            RenderWorld renderWorld,
            Camera camera,
            Player player,
            Physics physics,
            World world,
            IWorldAuthority authority,
            DeltaTracker delta,
            InputMapper mapper,
            DebugRenderer debugRenderer,
            HudRenderer hudRenderer) {
        this.window = window;
        this.renderer = renderer;
        this.chunkManager = chunkManager;
        this.renderWorld = renderWorld;
        this.camera = camera;
        this.player = player;
        this.physics = physics;
        this.world = world;
        this.authority = authority;
        this.delta = delta;
        this.mapper = mapper;
        this.debugRenderer = debugRenderer;
        this.hudRenderer = hudRenderer;
    }

    public void run() {
        while (!window.shouldClose()) {
            processTitle();
            processInput();
            processPhysics();
            processChunks();

            hudRenderer.setHotbar(player.getHotbarSnapshot(), player.getSelectedSlot());
            window.beginFrame();
            renderer.draw(camera);
            window.endFrame();
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
            window.setTitle("Minecraft FPS: %d".formatted(delta.getFrames()));
            delta.reset();
        }
    }

    private void processInput() {
        window.pollEvents();
        List<IInputAction> actions = mapper.drain(delta.getDelta());

        if (actions.contains(IInputAction.Simple.EXIT)) {
            window.close();
        }

        if (actions.contains(IInputAction.Simple.RELOAD_SHADERS)) {
            renderer.reloadAll();
        }

        world.update(actions, delta.getDelta());
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
        if (physicsReady() && !player.isFlyMode()) {
            physics.update(player, authority, dt);
        } else if (player.isFlyMode()) {
            // Still integrate velocity in fly mode — Player sets it, we move the position.
            player.getPosition()
                    .add(player.getVelocity().x * dt, player.getVelocity().y * dt, player.getVelocity().z * dt);
        }
        player.syncCamera();
    }

    private void processChunks() {
        // Upload at most MAX_UPLOADS_PER_FRAME new meshes — GpuMesh asserts main thread.
        // Skip empty buffers so chunks with no opaque (or no transparent) geometry don't
        // allocate a zero-length VAO; null means "nothing to draw for this pass".
        for (ChunkMeshData data : chunkManager.drainUploadQueue(MAX_UPLOADS_PER_FRAME)) {
            ChunkMesh opaque = data.opaque().isEmpty() ? null : new ChunkMesh(data.opaque());
            ChunkMesh transparent = data.transparent().isEmpty() ? null : new ChunkMesh(data.transparent());
            renderWorld.add(data.pos(), opaque, transparent);
            data.chunk().tryTransition(ChunkState.UPLOADED);
        }

        // Free GL buffers for chunks that left the load radius.
        for (var pos : chunkManager.drainUnloadQueue()) {
            RenderWorld.Entry entry = renderWorld.remove(pos);
            if (entry != null) entry.delete();
        }
    }
}
