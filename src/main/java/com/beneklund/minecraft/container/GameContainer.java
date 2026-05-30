package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.audio.StbAudioLoader;
import com.beneklund.minecraft.platform.images.StbImageLoader;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.resources.JsonResourcePack;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.platform.window.WindowConfig;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.renderer.TextureAtlas;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.util.Direction;
import com.beneklund.minecraft.world.World;
import java.io.IOException;
import org.joml.Vector3f;

public class GameContainer {
    public void run() throws IOException {
        // 1. Config - pure data, no platform deps.
        LocalConfig localConfig = new LocalConfig();
        WindowConfig config = new WindowConfig("Minecraft", 800, 600, false, Color.SKY);

        // 2. Input plumbing - pure Java, no GLFW/GL yet.
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);

        // 3. Pre-init platform objects - constructed but not yet active.
        Window window = new Window(config, queue);
        Camera camera = new Camera(config, new Vector3f(0.0f, 0.0f, -3.0f), 45.0f);
        InputHandler handler = new InputHandler(window, camera);
        DeltaTracker delta = new DeltaTracker(window::getTime);
        window.addResizeListener(camera::setWindowSize);

        // 4. window.init() - creates the GLFW window and makes the GL context current.
        //    Nothing that calls GL or uploads to the GPU may run before this line.
        window.init();

        // 5. GL resources - shaders, VAOs, textures. Requires active GL context.
        // Renderer triangleRenderer = getTriangleRenderer(); // oak-leaf textured triangle

        JsonResourcePack resourcePack = new JsonResourcePack("/packs/faithful/pack.json", new StbImageLoader());
        TextureAtlas atlas = new TextureAtlas(resourcePack);
        BlockRegistry registry = BlockRegistry.createDefault();

        Renderer renderer = getCubeRenderer(atlas, registry.get(Block.GRASS));

        // 6. Audio - OpenAL is lazy-initialized on first play(), but construct after GL
        //    so the window is confirmed healthy before we open the audio device.
        AudioPlayer music = new AudioPlayer(new StbAudioLoader());
        localConfig.startupDisc().ifPresent(music::play);

        // 7. Game logic - depends on input and the GL renderer being ready.
        World world = new World(handler);
        new Game(window, renderer, camera, world, delta, mapper).run();

        // 8. Shutdown - reverse dependency order: audio before window (AL before GLFW/GL).
        music.shutdown();
        window.shutdown();
    }

    private Renderer getCubeRenderer(TextureAtlas atlas, BlockDef def) {
        // Each face is 4 unique vertices (position + uv) so UVs wrap cleanly per face.
        // 24 vertices total: 4 per face * 6 faces. Each row below is one vertex: x, y, z, u, v.
        // spotless:off
        float[] s = atlas.getFaceUVs(def, Direction.SOUTH);
        float[] n = atlas.getFaceUVs(def, Direction.NORTH);
        float[] w = atlas.getFaceUVs(def, Direction.WEST);
        float[] e = atlas.getFaceUVs(def, Direction.EAST);
        float[] u = atlas.getFaceUVs(def, Direction.UP);
        float[] d = atlas.getFaceUVs(def, Direction.DOWN);
        float[] vertices = {
            // front / SOUTH (z = +0.5)
            -0.5f,  0.5f,  0.5f,   s[0], s[3],
            -0.5f, -0.5f,  0.5f,   s[0], s[1],
             0.5f, -0.5f,  0.5f,   s[2], s[1],
             0.5f,  0.5f,  0.5f,   s[2], s[3],
            // back / NORTH (z = -0.5)
             0.5f,  0.5f, -0.5f,   n[0], n[3],
             0.5f, -0.5f, -0.5f,   n[0], n[1],
            -0.5f, -0.5f, -0.5f,   n[2], n[1],
            -0.5f,  0.5f, -0.5f,   n[2], n[3],
            // left / WEST (x = -0.5)
            -0.5f,  0.5f, -0.5f,   w[0], w[3],
            -0.5f, -0.5f, -0.5f,   w[0], w[1],
            -0.5f, -0.5f,  0.5f,   w[2], w[1],
            -0.5f,  0.5f,  0.5f,   w[2], w[3],
            // right / EAST (x = +0.5)
             0.5f,  0.5f,  0.5f,   e[0], e[3],
             0.5f, -0.5f,  0.5f,   e[0], e[1],
             0.5f, -0.5f, -0.5f,   e[2], e[1],
             0.5f,  0.5f, -0.5f,   e[2], e[3],
            // top / UP (y = +0.5)
            -0.5f,  0.5f, -0.5f,   u[0], u[3],
            -0.5f,  0.5f,  0.5f,   u[0], u[1],
             0.5f,  0.5f,  0.5f,   u[2], u[1],
             0.5f,  0.5f, -0.5f,   u[2], u[3],
            // bottom / DOWN (y = -0.5)
            -0.5f, -0.5f,  0.5f,   d[0], d[3],
            -0.5f, -0.5f, -0.5f,   d[0], d[1],
             0.5f, -0.5f, -0.5f,   d[2], d[1],
             0.5f, -0.5f,  0.5f,   d[2], d[3],
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

        return new Renderer("/shaders/cube.vert", "/shaders/cube.frag", vertices, indices, atlas);
    }

    @SuppressWarnings("unused")
    private Renderer getTriangleRenderer(TextureAtlas atlas) {
        float[] uvs = atlas.getUVs("oak_leaves");
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        // spotless:off
        float[] vertices = {
             0.0f,  0.5f, 0.0f,   (uMin + uMax) / 2f, vMax,
            -0.5f, -0.5f, 0.0f,   uMin, vMin,
             0.5f, -0.5f, 0.0f,   uMax, vMin,
        };
        // spotless:on
        int[] indices = {0, 1, 2};
        return new Renderer("/shaders/triangle.vert", "/shaders/triangle.frag", vertices, indices, atlas);
    }
}
