package com.beneklund.minecraft.util;

/*
 * The main thread's frame, cut into named regions.
 *
 * There are GPU timers per render pass and there is a frame-time ring buffer, and between them
 * they answer "how long was the frame" and "how much of it was the GPU". Neither answers the
 * question that matters when those two disagree: the frame is 25 ms, the GPU did 5 ms of it, so
 * where did the other 20 go. That gap is what this measures.
 *
 * Flat and sequential on purpose, exactly like the GPU pass timers. A region ends before the next
 * begins, so the parts sum to something close to the frame and a missing region shows up as a
 * shortfall rather than hiding inside a parent. SWAP is last and absorbs whatever the driver makes
 * us wait for, which is the one region that is not really CPU work — it is the CPU standing still,
 * and telling those apart is the whole point.
 */
public enum CpuPhase {
    /** `processInput` — drain the queue, map to actions, run them, raycast the targeted block. */
    INPUT,

    /** `processPhysics` — the fixed-step loop and the camera sync. */
    PHYSICS,

    /**
     * `processChunks` — draining the upload queue and creating GL buffers, plus `ChunkManager.tick`
     * if that is still on the main thread. The prime suspect for the standing-still-to-flying gap.
     */
    CHUNKS,

    /** `drawScene` — collecting every renderable's draw calls, then the shadow, cloud and scene passes. */
    SCENE,

    /** `PostProcessor.draw` — the CPU cost of issuing it, not the GPU cost of running it. */
    POST,

    /** `drawHud`. */
    HUD,

    /** `glReadPixels` plus the copy handed to the encoder. Zero unless a viewer is watching. */
    CAPTURE,

    /**
     * `glfwSwapBuffers`. Mostly the CPU waiting for the GPU or for vsync, so a large SWAP with
     * small everything else means the frame is GPU-bound and the CPU had nothing to do.
     */
    SWAP;

    public static final int COUNT = values().length;
}
