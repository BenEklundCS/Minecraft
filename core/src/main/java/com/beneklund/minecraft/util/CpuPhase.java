package com.beneklund.minecraft.util;

/**
 * The main thread's frame, cut into named, sequential regions for CPU timing.
 *
 * <p>The GPU pass timers and the frame-time ring buffer say how long a frame took and how much of
 * it the GPU spent. These regions account for the rest: a 25 ms frame with 5 ms of GPU work has 20
 * ms to find here.
 *
 * <p>Regions are flat, like the GPU pass timers. Each ends before the next begins, so the parts sum
 * to roughly the frame and an unmeasured stretch shows as a shortfall. {@link #SWAP} is last and
 * holds the time the CPU spends waiting on the driver, which separates idle from work.
 */
public enum CpuPhase {
    /** {@code processInput}: drain the input queue, map to actions, run them, raycast the target. */
    INPUT,

    /** {@code processPhysics}: the fixed-step loop and the camera sync. */
    PHYSICS,

    /**
     * {@code processChunks}: server packets, mesh uploads and GL buffer creation, and unloaded
     * chunks' buffer deletes. Near zero standing still and large while flying, which is the gap
     * this instrument exists to size.
     */
    CHUNKS,

    /** {@code drawScene}: collect every renderable's draw calls, then the shadow, cloud and scene passes. */
    SCENE,

    /** {@code PostProcessor.draw}: the CPU cost of issuing the post passes. The GPU cost is timed separately. */
    POST,

    /** {@code drawHud}. */
    HUD,

    /** {@code glReadPixels} plus the copy handed to the encoder. Zero unless a viewer is watching. */
    CAPTURE,

    /**
     * {@code glfwSwapBuffers}. Mostly the CPU waiting for the GPU or for vsync, so a large SWAP
     * with everything else small means the frame is GPU-bound.
     */
    SWAP;

    public static final int COUNT = values().length;
}
