package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.platform.window.WindowConfig;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.FrameTimeCounter;

public class GameContainer {
    public void run() {
        WindowConfig config = new WindowConfig("Minecraft", 800, 600, false, Color.SKY);

        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);
        Window window = new Window(config, queue);
        InputHandler handler = new InputHandler(window);
        FrameTimeCounter counter = new FrameTimeCounter(window::getTime);

        window.init();

        new Game(window, counter, mapper, handler).run();

        window.shutdown();
    }
}
