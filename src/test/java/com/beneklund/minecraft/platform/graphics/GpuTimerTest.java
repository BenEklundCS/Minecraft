package com.beneklund.minecraft.platform.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/*
 * slotFor and readableFrame are the whole correctness argument for the timer, and neither needs
 * a GL context - which is why they are extracted at all. Everything else in GpuTimer issues GL
 * calls and belongs to the runtime checkpoint instead.
 *
 * What these pin: frame N writes slot N % TIMER_ROTATION and reads the slot written
 * TIMER_ROTATION - 1 frames ago, which has had two full frames to drain. The failure mode is an
 * off-by-one - readableFrame(n) = n - 1 reads a slot the GPU may still be filling, so
 * glGetQueryObjectui64 blocks and the instrument stalls the thing it is measuring. These tests
 * catch the arithmetic; only the runtime check catches the stall.
 */
class GpuTimerTest {

    @Test
    void slotRotates() {
        assertEquals(0, GpuTimer.slotFor(0));
        assertEquals(1, GpuTimer.slotFor(1));
        assertEquals(2, GpuTimer.slotFor(2));
        assertEquals(0, GpuTimer.slotFor(3));
    }

    @Test
    void readableFrameLagsByRotation() {
        assertEquals(8, GpuTimer.readableFrame(10));
        // Negative means "nothing has been written yet" - the startup window, before any frame
        // has completed a full rotation. lastResultNanos turns this into -1, not a plausible 0.
        assertTrue(GpuTimer.readableFrame(0) < 0, "frame 0 has nothing readable behind it");
    }

    @Test
    void readableFrameIsAlwaysAWholeRotationBehindTheWriteSlot() {
        // The property that matters more than any single value: the slot being read this frame is
        // never the slot being written this frame, for any frame. If these ever collide, a read
        // lands on a query the GPU is still filling.
        for (long frame = TIMER_ROTATION_MINUS_ONE; frame < 64; frame++) {
            assertTrue(
                    GpuTimer.slotFor(frame) != GpuTimer.slotFor(GpuTimer.readableFrame(frame)),
                    "write and read slots collided on frame " + frame);
        }
    }

    private static final long TIMER_ROTATION_MINUS_ONE = GpuTimer.TIMER_ROTATION - 1;

    @Test
    void slotForNeverReturnsANegativeIndex() {
        // floorMod rather than %: a plain remainder on a negative long is negative, and these
        // values index an array directly.
        assertTrue(GpuTimer.slotFor(-1) >= 0);
        assertTrue(GpuTimer.slotFor(-7) >= 0);
    }
}
