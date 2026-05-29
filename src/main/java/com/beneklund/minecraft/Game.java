package com.beneklund.minecraft;

import com.beneklund.minecraft.platform.input.InputAction;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;

import java.util.List;

public record Game(Window window, Renderer renderer, World world, DeltaTracker delta, InputMapper mapper) {
    public void run() {
        while (!this.window.shouldClose()) {
            this.delta.tick();
            if (this.delta.timePassed(1.0f)) {
                this.window.setTitle("Minecraft FPS: " + this.delta.getFrames());
                this.delta.reset();
            }
            this.window.pollEvents();
            List<InputAction> actions = this.mapper.drain();
            if (actions.contains(InputAction.Simple.EXIT)) { this.window.close(); }
            this.world.update(actions, this.delta.getDelta());
            this.window.beginFrame();
            this.renderer.render();
            this.window.endFrame();
        }
    }
}
