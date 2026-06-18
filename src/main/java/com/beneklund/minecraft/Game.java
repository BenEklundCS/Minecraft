package com.beneklund.minecraft;

import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Interaction;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.*;
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
    // Cap the physics step. Collision is discrete (checks the destination cell, not the swept
    // path), so a long frame — a GC pause or the spawn-time mesh-upload storm — could otherwise
    // move the body far enough in one step to skip clean through a block. Clamping trades a
    // momentary slow-down during a hitch for never tunnelling.
    private static final float MAX_PHYSICS_STEP = 1.0f / 20.0f;

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
            delta.tick();
            if (delta.timePassed(1.0f)) {
                window.setTitle("Minecraft FPS: %d".formatted(delta.getFrames()));
                delta.reset();
            }

            window.pollEvents();
            List<IInputAction> actions = mapper.drain(delta.getDelta());

            if (actions.contains(IInputAction.Simple.EXIT)) {
                window.close();
            }

            world.update(actions, delta.getDelta());
            chunkManager.tick(player.getChunkPos());

            List<Interaction> interactions = player.tick(actions);
            for (Interaction interaction : interactions) {
                if (interaction
                        instanceof
                        Interaction.BlockInteraction(
                                boolean broken,
                                Vector3f eye,
                                Vector3f dir,
                                RaycastResult result)) {
                    if (broken) {
                        debugRenderer.updateFromRaycast(eye, dir, result);
                    }
                }
            }

            debugRenderer.updateTargetedBlock(player.getTargetedBlock());

            if (physicsReady()) {
                physics.update(player, authority, Math.min(delta.getDelta(), MAX_PHYSICS_STEP));
            }
            player.syncCamera();

            // Upload at most MAX_UPLOADS_PER_FRAME new meshes — GpuMesh asserts main thread.
            for (ChunkMeshData data : chunkManager.drainUploadQueue(MAX_UPLOADS_PER_FRAME)) {
                GpuMesh mesh = new GpuMesh(data.vertices(), data.indices());
                renderWorld.add(data.pos(), mesh);
                data.chunk().tryTransition(ChunkState.UPLOADED);
            }

            // Free GL buffers for chunks that left the load radius.
            for (var pos : chunkManager.drainUnloadQueue()) {
                RenderWorld.Entry entry = renderWorld.remove(pos);
                if (entry != null) entry.mesh().delete();
            }

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
}
