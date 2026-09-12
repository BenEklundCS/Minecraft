package com.beneklund.minecraft.util;

/*
Converts a variable frame delta (dt) into a whole number of fixed simulation steps
 */
public class FixedTimestep {
    public static final float STEP_SECONDS = 1.0f / 60.0f;
    public static final int MAX_STEPS_PER_FRAME = 5;

    private float accumulator = 0.0f;

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

    public float remainder() {
        return accumulator;
    }
}
