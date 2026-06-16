package com.beneklund.minecraft;

import static com.beneklund.minecraft.util.Log.LOGGER;

import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.renderer.DebugRenderer;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.util.Raycast;
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
    private static final float REACH = 8.0f;

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
            DebugRenderer debugRenderer) {
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
    }

    public void run() {
        while (!window.shouldClose()) {
            delta.tick();
            if (delta.timePassed(1.0f)) {
                window.setTitle("Minecraft FPS: %d".formatted(delta.getFrames()));
                delta.reset();
            }

            window.pollEvents();
            List<IInputAction> actions = mapper.drain();

            if (actions.contains(IInputAction.Simple.EXIT)) {
                window.close();
            }

            world.update(actions, delta.getDelta());
            chunkManager.tick(player.getChunkPos());

            for (var action : actions) {
                if (action == IInputAction.Simple.BREAK_BLOCK) {
                    Vector3f eyePos = new Vector3f(player.getPosition()).add(0, Player.EYE_HEIGHT, 0);
                    Vector3f lookDir = player.getLookDirection();
                    RaycastResult result = Raycast.cast(eyePos, lookDir, authority, REACH);
                    LOGGER.info(
                            "Raycast hit={} blockPos={} face={} distance={}",
                            result.hit(),
                            result.blockPos(),
                            result.hitFace(),
                            String.format("%.2f", result.distance()));
                    debugRenderer.updateFromRaycast(eyePos, lookDir, result);
                }
            }
            // input sets intent (velocity, jump), physics integrates + resolves collisions,
            // then the camera follows the player's settled position. Hold physics until the
            // player's chunk is generated so we don't fall through not-yet-filled terrain.
            player.tick(actions);
            if (physicsReady()) {
                physics.update(player, authority, delta.getDelta());
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
