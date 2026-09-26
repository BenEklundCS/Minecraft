package com.beneklund.minecraft.util;

/**
 * Converts a variable frame delta into a whole number of fixed 60 Hz simulation steps.
 *
 * <p>Frame time accumulates, and each full {@link #STEP_SECONDS} in the accumulator is one step;
 * the leftover carries into the next frame. Physics then sees the same {@code dt} every step, so
 * jump height and collision behave identically at 30 FPS and 240 FPS.
 *
 * <p>A frame yields at most {@link #MAX_STEPS_PER_FRAME} steps, and any backlog beyond that is
 * dropped. Without the cap, a slow frame queues more steps, which makes the next frame slower
 * still: Fiedler's "spiral of death". The cost is that the simulation runs slow during a long
 * hitch.
 *
 * @see <a href="https://gafferongames.com/post/fix_your_timestep/">Glenn Fiedler: Fix Your
 *     Timestep!</a>
 */
public class FixedTimestep {
    public static final float STEP_SECONDS = 1.0f / 60.0f;
    public static final int MAX_STEPS_PER_FRAME = 5;

    private float accumulator = 0.0f;

    /** Adds {@code dt} seconds and returns how many steps to run this frame. */
    public int stepsFor(float dt) {
        accumulator += dt;
        int steps = 0;
        while (accumulator >= STEP_SECONDS && steps < MAX_STEPS_PER_FRAME) {
            accumulator -= STEP_SECONDS;
            steps++;
        }
        if (accumulator >= STEP_SECONDS) accumulator = 0.0f;
        return steps;
    }

    /** Seconds left in the accumulator, always less than one step. */
    public float remainder() {
        return accumulator;
    }
}
