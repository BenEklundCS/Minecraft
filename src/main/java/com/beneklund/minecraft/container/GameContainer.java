package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.MusicPlayer;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.platform.window.WindowConfig;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;

public class GameContainer {
    public void run() {
        LocalConfig localConfig = new LocalConfig();
        WindowConfig config = new WindowConfig("Minecraft", 800, 600, false, Color.SKY);

        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);
        Window window = new Window(config, queue);
        InputHandler handler = new InputHandler(window);
        DeltaTracker delta = new DeltaTracker(window::getTime);

        window.init();

        Renderer renderer = getRenderer();

        MusicPlayer music = new MusicPlayer();
        localConfig.startupDisc().ifPresent(music::play);

        World world = new World(handler);
        new Game(window, renderer, world, delta, mapper).run();

        music.shutdown();
        window.shutdown();
    }

    private Renderer getRenderer() {
        Color c = Color.OAK_LEAF;
        String vertexSource = """
                #version 330 core
                layout(location = 0) in vec3 position;
                void main() {
                    gl_Position = vec4(position, 1.0);
                }
                """;
        String fragmentSource = """
                #version 330 core
                out vec4 FragColor;
                void main() {
                    FragColor = vec4(%f, %f, %f, %f);
                }
                """.formatted(c.red(), c.green(), c.blue(), c.alpha());

        float[] vertices = {
                0.0f,  0.5f, 0.0f,
                -0.5f, -0.5f, 0.0f,
                0.5f, -0.5f, 0.0f
        };

        return new Renderer(vertexSource, fragmentSource, vertices);
    }
}
