package com.beneklund.minecraft;

import com.beneklund.minecraft.input.InputAction;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.ChunkRenderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;
import java.util.List;
import org.joml.Matrix4f;

public class Game {

    private static final float MOUSE_SENSITIVITY = 0.15f;

    private final Window window;
    private final ChunkRenderer chunkRenderer;
    private final Camera camera;
    private final World world;
    private final DeltaTracker delta;
    private final InputMapper mapper;

    public Game(
            Window window,
            ChunkRenderer chunkRenderer,
            Camera camera,
            World world,
            DeltaTracker delta,
            InputMapper mapper) {
        this.window = window;
        this.chunkRenderer = chunkRenderer;
        this.camera = camera;
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
            List<InputAction> actions = mapper.drain();

            if (actions.contains(InputAction.Simple.EXIT)) {
                window.close();
            }

            world.update(actions, delta.getDelta());

            for (var action : actions) {
                switch (action) {
                    case InputAction.MoveAction(float dx, float dz) ->
                        camera.moveRelative(dz, dx, (float) delta.getDelta());
                    case InputAction.LookAction(float dx, float dy) ->
                        camera.look(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
                    default -> {}
                }
            }

            Matrix4f view = camera.getViewMatrix();
            Matrix4f projection = camera.getProjectionMatrix();

            window.beginFrame();
            chunkRenderer.render(view, projection);
            window.endFrame();
        }
    }
}
