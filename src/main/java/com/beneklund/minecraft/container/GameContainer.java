package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.MusicPlayer;
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
        Camera camera = new Camera(config, new Vector3f(0.0f, 0.0f, 0.0f), 45.0f);
        InputHandler handler = new InputHandler(window, camera);
        DeltaTracker delta = new DeltaTracker(window::getTime);


        window.addResizeListener(camera::setWindowSize);

        window.init();

        Renderer renderer = getRenderer();

        MusicPlayer music = new MusicPlayer();
        localConfig.startupDisc().ifPresent(music::play);

        World world = new World(handler);
        new Game(window, renderer, camera, world, delta, mapper).run();

        music.shutdown();
        window.shutdown();
    }

    private Renderer getRenderer() {
        // view/projection are the camera matrices; the vertex stage transforms each position by
        // them. The Renderer uploads both every frame.
        String vertexSource = """
                #version 330 core
                layout(location = 0) in vec3 position;
                layout(location = 1) in vec2 uv;
                out vec2 texCoord;
                uniform mat4 view;
                uniform mat4 projection;
                void main() {
                    gl_Position = projection * view * vec4(position, 1.0);
                    texCoord = uv;
                }
                """;
        Color c = Color.OAK_LEAF;
        String fragmentSource = """
                #version 330 core
                in vec2 texCoord;
                out vec4 FragColor;
                uniform sampler2D tex;
                void main() {
                    FragColor = texture(tex, texCoord) * vec4(%f, %f, %f, %f);
                }
                """.formatted(c.red(), c.green(), c.blue(), c.alpha());

        // x, y, z, u, v per vertex
        float[] vertices = {
                 0.0f,  0.5f, 0.0f,   0.5f, 1.0f,
                -0.5f, -0.5f, 0.0f,   0.0f, 0.0f,
                 0.5f, -0.5f, 0.0f,   1.0f, 0.0f
        };

        return new Renderer(vertexSource, fragmentSource, vertices, "/packs/faithful/textures/default_leaves.png");
    }
}
