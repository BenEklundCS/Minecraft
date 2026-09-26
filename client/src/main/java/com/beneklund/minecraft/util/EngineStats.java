package com.beneklund.minecraft.util;

import com.beneklund.minecraft.renderer.RenderPass;
import java.util.Arrays;

/**
 * Per-frame render counters and CPU phase timings, double-buffered: {@code count*} and {@code
 * *Phase} write the frame being built, the getters read the last finished one.
 *
 * <p>Static, and the only global state in the tree. The write sites span layers that can't reach a
 * shared instance ({@code GlShader} in {@code platform/graphics} may not import {@code renderer}),
 * so a collector threaded through constructors would cost four files per new counter. It stays
 * safe because nothing branches on these values: they are written and displayed, and a counter
 * stuck at zero changes no pixel and no frame time.
 *
 * <p>Increments are plain {@code ++}. Uniform uploads number in the tens of thousands per frame,
 * and atomics would add cost to the hot path these numbers measure. The price is that only the
 * main thread may call anything here.
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

    /**
     * Publishes the frame just finished to the getters and zeroes the accumulators. Call once at
     * the top of the game loop.
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

    /**
     * Opens a timed region; {@link #endPhase} adds its duration. Regions are flat and sequential.
     * A begin without an end records nothing and throws nothing, so phases that don't sum to the
     * frame time are the sign of an unbalanced pair.
     */
    public static void beginPhase(CpuPhase phase) {
        phaseStart[phase.ordinal()] = System.nanoTime();
    }

    public static void endPhase(CpuPhase phase) {
        phaseNanos[phase.ordinal()] += System.nanoTime() - phaseStart[phase.ordinal()];
    }

    /** Time spent in {@code phase} during the last finished frame, in milliseconds. */
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

    /**
     * Zeroes both buffers. For tests: statics survive between test classes in one JVM, so anything
     * asserting on these starts from here.
     */
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
