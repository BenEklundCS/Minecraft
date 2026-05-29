package com.beneklund.minecraft.util;

import java.util.function.DoubleSupplier;

public class DeltaTracker {
    private final DoubleSupplier clock;
    private double lastTime;
    private double currentTime;
    private double prevFrameTime;
    private int frames;

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
