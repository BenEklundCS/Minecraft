package com.beneklund.minecraft;

import com.beneklund.minecraft.input.InputAction;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.World;
import java.util.List;

public record Game(Window window, Renderer renderer, Camera camera, World world, DeltaTracker delta, InputMapper mapper) {
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

            // Orbit around the origin on a circle (sin/cos of elapsed time), lifted to y=1.5 so the
            // flat triangle never goes edge-on. View targets the origin, so moving position is enough.
            float t = (float) this.window.getTime();
            float radius = 3.0f;
            this.camera.setPosition((float) Math.sin(t) * radius, 1.5f, (float) Math.cos(t) * radius);

            this.window.beginFrame();
            this.renderer.render(this.camera.getViewMatrix(), this.camera.getProjectionMatrix());
            this.window.endFrame();
        }
    }
}
