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
        prevFrameTime = currentTime;
        currentTime = clock.getAsDouble();
        frames++;
    }

    public float getDelta() {
        return (float) (currentTime - prevFrameTime);
    }

    public boolean timePassed(double time) {
        return currentTime - lastTime >= time;
    }

    public int getFrames() {
        return frames;
    }

    public void reset() {
        lastTime = clock.getAsDouble();
        frames = 0;
    }
}
