package com.beneklund.minecraft;

import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.renderer.ChunkRenderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;

import java.util.List;
import org.joml.Matrix4f;

// Per-frame update/render loop. Drives player input, chunk streaming, GPU uploads, and rendering.
public class Game {

    // Scales raw mouse pixel delta to degrees. Lives here — Camera shouldn't know the input device.
    private static final float MOUSE_SENSITIVITY = 0.15f;
    // Cap chunk uploads per frame to avoid hitching when many chunks arrive at once.
    private static final int MAX_UPLOADS_PER_FRAME = 4;

    private final Window window;
    private final ChunkRenderer chunkRenderer;
    private final ChunkManager chunkManager;
    private final RenderWorld renderWorld;
    private final Camera camera;
    private final Player player;
    private final World world;
    private final DeltaTracker delta;
    private final InputMapper mapper;

    public Game(
            Window window,
            ChunkRenderer chunkRenderer,
            ChunkManager chunkManager,
            RenderWorld renderWorld,
            Camera camera,
            Player player,
            World world,
            DeltaTracker delta,
            InputMapper mapper) {
        this.window = window;
        this.chunkRenderer = chunkRenderer;
        this.chunkManager = chunkManager;
        this.renderWorld = renderWorld;
        this.camera = camera;
        this.player = player;
        this.world = world;
        this.delta = delta;
        this.mapper = mapper;
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
                switch (action) {
                    case IInputAction.MoveActionI(float dx, float dz) ->
                        player.moveRelative(dz, dx, (float) delta.getDelta());
                    case IInputAction.LookActionI(float dx, float dy) ->
                        player.look(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
                    default -> {}
                }
            }

            // Upload at most MAX_UPLOADS_PER_FRAME new meshes — GpuMesh asserts main thread.
            for (ChunkMeshData data : chunkManager.drainUploadQueue(MAX_UPLOADS_PER_FRAME)) {
                GpuMesh mesh = new GpuMesh(data.vertices(), data.indices());
                renderWorld.add(data.pos(), mesh);
            }

            // Free GL buffers for chunks that left the load radius.
            for (var pos : chunkManager.drainUnloadQueue()) {
                GpuMesh mesh = renderWorld.remove(pos);
                if (mesh != null) mesh.delete();
            }

            Matrix4f view = camera.getViewMatrix();
            Matrix4f projection = camera.getProjectionMatrix();

            window.beginFrame();
            chunkRenderer.render(renderWorld, view, projection);
            window.endFrame();
        }
    }
}
