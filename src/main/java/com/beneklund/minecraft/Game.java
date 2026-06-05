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
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.ChunkState;
import com.beneklund.minecraft.world.World;
import java.util.List;

// Per-frame update/render loop. Drives player input, chunk streaming, GPU uploads, and rendering.
public class Game {

    // Cap chunk uploads per frame to avoid hitching when many chunks arrive at once.
    private static final int MAX_UPLOADS_PER_FRAME = 4;

    private final Window window;
    private final Renderer renderer;
    private final ChunkManager chunkManager;
    private final RenderWorld renderWorld;
    private final Camera camera;
    private final Player player;
    private final World world;
    private final DeltaTracker delta;
    private final InputMapper mapper;

    public Game(
            Window window,
            Renderer renderer,
            ChunkManager chunkManager,
            RenderWorld renderWorld,
            Camera camera,
            Player player,
            World world,
            DeltaTracker delta,
            InputMapper mapper) {
        this.window = window;
        this.renderer = renderer;
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
            player.tick(actions, delta.getDelta());

            // Upload at most MAX_UPLOADS_PER_FRAME new meshes — GpuMesh asserts main thread.
            for (ChunkMeshData data : chunkManager.drainUploadQueue(MAX_UPLOADS_PER_FRAME)) {
                GpuMesh mesh = new GpuMesh(data.vertices(), data.indices());
                renderWorld.add(data.pos(), mesh);
                data.chunk().tryTransition(ChunkState.UPLOADED);
            }

            // Free GL buffers for chunks that left the load radius.
            for (var pos : chunkManager.drainUnloadQueue()) {
                GpuMesh mesh = renderWorld.remove(pos);
                if (mesh != null) mesh.delete();
            }

            window.beginFrame();
            renderer.draw(camera);
            window.endFrame();
        }
    }
}
