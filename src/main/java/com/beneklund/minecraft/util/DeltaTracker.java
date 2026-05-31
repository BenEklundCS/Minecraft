package com.beneklund.minecraft.util;

import java.util.function.DoubleSupplier;

// Tracks frame time so movement and physics can be scaled by elapsed seconds rather than
// frame count. Inject glfwGetTime as the clock in production; inject a fake clock in tests.
public class DeltaTracker {
    private final DoubleSupplier clock;
    private double lastTime; // timestamp of the last reset() call — used by timePassed()
    private double currentTime;
    private double prevFrameTime;
    private int frames; // frame count since last reset(), useful for FPS display

    public DeltaTracker(DoubleSupplier clock) {
        this.clock = clock;
        this.reset();
    }

    public void tick() {
        this.prevFrameTime = this.currentTime;
        this.currentTime = this.clock.getAsDouble();
        this.frames++;
    }

    public float getDelta() {
        return (float) (this.currentTime - this.prevFrameTime);
    }

    public boolean timePassed(double time) {
        return this.currentTime - this.lastTime >= time;
    }

    public int getFrames() {
        return this.frames;
    }

    public void reset() {
        this.lastTime = this.clock.getAsDouble();
        this.frames = 0;
    }
}
