package com.beneklund.minecraft.util;

import java.util.function.DoubleSupplier;

/**
 * The wall-clock time between frames, in seconds, and a frame counter for the FPS readout.
 *
 * <p>The clock is injected: the window's {@code glfwGetTime} in the game, a fake in tests. {@code
 * Game} feeds {@link #getDelta()} into {@link FixedTimestep}, which turns the variable delta into
 * fixed physics steps.
 *
 * @see <a href="https://gafferongames.com/post/fix_your_timestep/">Glenn Fiedler: Fix Your
 *     Timestep!</a>
 */
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

    /** Samples the clock. Call once per frame, before reading {@link #getDelta()}. */
    public void tick() {
        prevFrameTime = currentTime;
        currentTime = clock.getAsDouble();
        frames++;
    }

    /** Seconds between the last two {@link #tick()} calls. */
    public float getDelta() {
        return (float) (currentTime - prevFrameTime);
    }

    /** Whether {@code time} seconds have passed between the last {@link #reset()} and the last tick. */
    public boolean timePassed(double time) {
        return currentTime - lastTime >= time;
    }

    /** Ticks since the last {@link #reset()}. */
    public int getFrames() {
        return frames;
    }

    /** Starts a new counting window for {@link #timePassed} and {@link #getFrames()}. */
    public void reset() {
        lastTime = clock.getAsDouble();
        frames = 0;
    }
}
