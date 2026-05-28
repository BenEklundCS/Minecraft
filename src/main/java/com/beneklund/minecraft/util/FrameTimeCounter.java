package com.beneklund.minecraft.util;

import java.util.function.DoubleSupplier;

public class FrameTimeCounter {
    private final DoubleSupplier clock;
    private double lastTime;
    private double currentTime;
    private int frames;

    public FrameTimeCounter(DoubleSupplier clock) {
        this.clock = clock;
        this.reset();
    }

    public void tick() {
        this.currentTime = this.clock.getAsDouble();
        this.frames++;
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
