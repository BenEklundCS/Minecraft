package com.beneklund.minecraft.util;

import com.beneklund.minecraft.renderer.RenderPass;
import java.util.Arrays;

/*
 * Per-frame render counters. Static on purpose, and the only globals in the tree that are:
 * the write sites are scattered across layers that cannot reach an instance - GlShader lives in
 * platform/graphics/ and the dependency rule forbids it importing renderer/ - so threading a
 * collector through every constructor would cost four files per new counter.
 *
 * What makes that acceptable is that nothing ever branches on these. They are written and
 * printed, never read back by the code being measured, so a counter that stayed zero forever
 * would not change a single pixel or a single frame.
 *
 * The increments are plain int++, not atomics, and that is load-bearing rather than lazy.
 * Uniform uploads run in the tens of thousands per frame; paying atomic contention that many
 * times a frame would add cost to the exact hot path these numbers exist to measure. The price
 * is that only the main thread may touch them - see beginFrame.
 */
public final class EngineStats {

    private EngineStats() {}

    /*
     * Live accumulators for the frame being built. Main thread only.
     *
     * Plain int[] indexed by RenderPass.ordinal(), not an EnumMap<RenderPass, Integer>. The map
     * was boxing an Integer on every count - and countVertices runs once per submitted draw, so
     * at ~1,200 draws a frame that was a few hundred thousand short-lived Integers a second on
     * the render thread. Vertex counts are far past the Integer cache, so every one allocated.
     *
     * This is a measurement tool; it has no business showing up in the allocation profile of the
     * thing it measures. It did - JFR put Integer among the most-allocated classes in the process.
     */
    private static final int PASS_COUNT = RenderPass.values().length;
    private static final int[] drawCalls = new int[PASS_COUNT];
    private static final int[] vertices = new int[PASS_COUNT];
    private static int uniformUploads;
    private static int chunksConsidered;
    private static int chunksDrawn;

    // The last frame that finished. Everything that reports reads these, never the accumulators
    // above, so a reader can never catch a half-built frame and wonder why opaque draws halved.
    /*
     * Wall-clock nanos per main-thread region for the frame being built, and where the open one
     * started. Same main-thread-only contract as the counters above, and the same reason it is
     * safe: nothing branches on these.
     *
     * nanoTime rather than currentTimeMillis - a 25 ms frame cut into eight regions has parts
     * measured in hundreds of microseconds, and millisecond resolution would read most of them
     * as zero.
     */
    private static final long[] phaseNanos = new long[CpuPhase.COUNT];
    private static final long[] phaseStart = new long[CpuPhase.COUNT];
    private static final long[] lastPhaseNanos = new long[CpuPhase.COUNT];

    private static final int[] lastDrawCalls = new int[PASS_COUNT];
    private static final int[] lastVertices = new int[PASS_COUNT];
    private static int lastUniformUploads;
    private static int lastChunksConsidered;
    private static int lastChunksDrawn;

    /*
     * Closes the frame just finished and opens the next one. Call once at the top of the game
     * loop, from the main thread.
     *
     */
    public static void beginFrame() {
        System.arraycopy(phaseNanos, 0, lastPhaseNanos, 0, CpuPhase.COUNT);
        Arrays.fill(phaseNanos, 0L);

        System.arraycopy(drawCalls, 0, lastDrawCalls, 0, PASS_COUNT);
        System.arraycopy(vertices, 0, lastVertices, 0, PASS_COUNT);
        lastUniformUploads = uniformUploads;
        lastChunksConsidered = chunksConsidered;
        lastChunksDrawn = chunksDrawn;

        Arrays.fill(drawCalls, 0);
        Arrays.fill(vertices, 0);
        uniformUploads = 0;
        chunksConsidered = 0;
        chunksDrawn = 0;
    }

    public static void countDrawCall(RenderPass pass) {
        drawCalls[pass.ordinal()]++;
    }

    public static void countVertices(RenderPass pass, int count) {
        vertices[pass.ordinal()] += count;
    }

    /*
     * Opens a region. Regions are flat and sequential, so an unbalanced begin is a region that
     * silently reads as zero rather than one that throws - which is the right trade for an
     * instrument, but it does mean the parts not summing to the frame is the signal to check.
     */
    public static void beginPhase(CpuPhase phase) {
        phaseStart[phase.ordinal()] = System.nanoTime();
    }

    public static void endPhase(CpuPhase phase) {
        phaseNanos[phase.ordinal()] += System.nanoTime() - phaseStart[phase.ordinal()];
    }

    // Last completed frame, in milliseconds.
    public static float phaseMillis(CpuPhase phase) {
        return lastPhaseNanos[phase.ordinal()] / 1_000_000.0f;
    }

    public static void countUniformUpload() {
        uniformUploads++;
    }

    public static void countChunkConsidered() {
        chunksConsidered++;
    }

    public static void countChunkDrawn() {
        chunksDrawn++;
    }

    public static int drawCalls(RenderPass pass) {
        return lastDrawCalls[pass.ordinal()];
    }

    public static int vertices(RenderPass pass) {
        return lastVertices[pass.ordinal()];
    }

    public static int uniformUploads() {
        return lastUniformUploads;
    }

    public static int chunksConsidered() {
        return lastChunksConsidered;
    }

    public static int chunksDrawn() {
        return lastChunksDrawn;
    }

    // Statics survive between test classes in a single JVM, so anything asserting on these has
    // to start from a known state.
    public static void reset() {
        Arrays.fill(phaseNanos, 0L);
        Arrays.fill(lastPhaseNanos, 0L);
        Arrays.fill(drawCalls, 0);
        Arrays.fill(lastDrawCalls, 0);
        Arrays.fill(vertices, 0);
        Arrays.fill(lastVertices, 0);
        uniformUploads = 0;
        chunksConsidered = 0;
        chunksDrawn = 0;
        lastUniformUploads = 0;
        lastChunksConsidered = 0;
        lastChunksDrawn = 0;
    }
}
