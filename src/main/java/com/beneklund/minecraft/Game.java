package com.beneklund.minecraft;

import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.util.FrameTimeCounter;

public record Game(Window window, FrameTimeCounter counter, InputMapper mapper, InputHandler handler) {
    public void run() {
        while (!this.window.shouldClose()) {
            this.counter.tick();
            if (this.counter.timePassed(1.0f)) {
                this.window.setTitle("Minecraft" + " FPS: " + this.counter.getFrames());
                this.counter.reset();
            }
            // poll
            this.window.pollEvents();
            // handle
            this.handler.handle(this.mapper.drain());
            // render
            this.window.beginFrame();
            this.window.endFrame();
        }
    }
}
