package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.platform.window.WindowConfig;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;
import org.joml.Vector3f;

public class GameContainer {
    public void run() {
        LocalConfig localConfig = new LocalConfig();
        WindowConfig config = new WindowConfig("Minecraft", 800, 600, false, Color.SKY);

        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);
        Window window = new Window(config, queue);
        Camera camera = new Camera(config, new Vector3f(0.0f, 0.0f, -3.0f), 45.0f);
        InputHandler handler = new InputHandler(window, camera);
        DeltaTracker delta = new DeltaTracker(window::getTime);

        window.addResizeListener(camera::setWindowSize);
        window.init();

        Renderer renderer = getCubeRenderer();
        // Renderer triangleRenderer = getTriangleRenderer(); // oak-leaf textured triangle

        AudioPlayer music = new AudioPlayer();
        localConfig.startupDisc().ifPresent(music::play);

        World world = new World(handler);
        new Game(window, renderer, camera, world, delta, mapper).run();

        music.shutdown();
        window.shutdown();
    }

    private Renderer getCubeRenderer() {
        // Each face is 4 unique vertices (position + uv) so UVs wrap cleanly per face.
        // 24 vertices total: 4 per face * 6 faces. Each row below is one vertex: x, y, z, u, v.
        // spotless:off
        float[] vertices = {
            // front (z = +0.5)
            -0.5f,  0.5f,  0.5f,   0.0f, 1.0f,
            -0.5f, -0.5f,  0.5f,   0.0f, 0.0f,
             0.5f, -0.5f,  0.5f,   1.0f, 0.0f,
             0.5f,  0.5f,  0.5f,   1.0f, 1.0f,
            // back (z = -0.5)
             0.5f,  0.5f, -0.5f,   0.0f, 1.0f,
             0.5f, -0.5f, -0.5f,   0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f,   1.0f, 0.0f,
            -0.5f,  0.5f, -0.5f,   1.0f, 1.0f,
            // left (x = -0.5)
            -0.5f,  0.5f, -0.5f,   0.0f, 1.0f,
            -0.5f, -0.5f, -0.5f,   0.0f, 0.0f,
            -0.5f, -0.5f,  0.5f,   1.0f, 0.0f,
            -0.5f,  0.5f,  0.5f,   1.0f, 1.0f,
            // right (x = +0.5)
             0.5f,  0.5f,  0.5f,   0.0f, 1.0f,
             0.5f, -0.5f,  0.5f,   0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,   1.0f, 0.0f,
             0.5f,  0.5f, -0.5f,   1.0f, 1.0f,
            // top (y = +0.5)
            -0.5f,  0.5f, -0.5f,   0.0f, 1.0f,
            -0.5f,  0.5f,  0.5f,   0.0f, 0.0f,
             0.5f,  0.5f,  0.5f,   1.0f, 0.0f,
             0.5f,  0.5f, -0.5f,   1.0f, 1.0f,
            // bottom (y = -0.5)
            -0.5f, -0.5f,  0.5f,   0.0f, 1.0f,
            -0.5f, -0.5f, -0.5f,   0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,   1.0f, 0.0f,
             0.5f, -0.5f,  0.5f,   1.0f, 1.0f,
        };

        // Each face is two triangles. Pattern per face: 0,1,2, 0,2,3 offset by face*4.
        int[] indices = {
             0,  1,  2,    0,  2,  3,  // front
             4,  5,  6,    4,  6,  7,  // back
             8,  9, 10,    8, 10, 11,  // left
            12, 13, 14,   12, 14, 15,  // right
            16, 17, 18,   16, 18, 19,  // top
            20, 21, 22,   20, 22, 23,  // bottom
        };
        // spotless:on

        return new Renderer(
                "/shaders/cube.vert",
                "/shaders/cube.frag",
                vertices,
                indices,
                "/packs/faithful/textures/default_leaves.png");
    }

    @SuppressWarnings("unused")
    private Renderer getTriangleRenderer() {
        // spotless:off
        float[] vertices = {
             0.0f,  0.5f, 0.0f,   0.5f, 1.0f,
            -0.5f, -0.5f, 0.0f,   0.0f, 0.0f,
             0.5f, -0.5f, 0.0f,   1.0f, 0.0f,
        };
        // spotless:on
        int[] indices = {0, 1, 2};
        return new Renderer(
                "/shaders/triangle.vert",
                "/shaders/triangle.frag",
                vertices,
                indices,
                "/packs/faithful/textures/default_leaves.png");
    }
}
