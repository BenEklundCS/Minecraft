package com.beneklund.minecraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedTimestepTest {
    // Frame lengths chosen so no case lands within float error of the step threshold — 30 ms
    // leaves 6.7 ms banked after frame 3, which is nowhere near 16.7.
    private static final float FRAME_30_MS = 0.030f;
    private static final float HITCH_250_MS = 0.250f;
    private static final float TOLERANCE = 1.0e-6f;

    @Test
    void steadyFramesSpendEveryBankedStepAndCarryTheRemainder() {
        FixedTimestep clock = new FixedTimestep();

        assertEquals(1, clock.stepsFor(FRAME_30_MS));
        assertEquals(2, clock.stepsFor(FRAME_30_MS));
        assertEquals(2, clock.stepsFor(FRAME_30_MS));

        // 0.090 elapsed - 5 * (1/60) simulated = 0.0066667 still banked.
        assertEquals(0.090f - 5 * FixedTimestep.STEP_SECONDS, clock.remainder(), TOLERANCE);
    }

    @Test
    void aHitchIsCappedAndTheBacklogIsDropped() {
        FixedTimestep clock = new FixedTimestep();

        assertEquals(FixedTimestep.MAX_STEPS_PER_FRAME, clock.stepsFor(HITCH_250_MS));
        // Dropped, not banked: the next frame must start clean or the cap achieves nothing.
        assertEquals(0.0f, clock.remainder(), TOLERANCE);
    }

    @Test
    void aFrameShorterThanAStepRunsNothingAndBanksIt() {
        FixedTimestep clock = new FixedTimestep();

        assertEquals(0, clock.stepsFor(0.010f));
        assertTrue(clock.remainder() > 0.0f);
    }
}
