package com.beneklund.minecraft.util;

import com.beneklund.minecraft.renderer.RenderPass;
import java.util.EnumMap;
import java.util.Map;

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

    // Live accumulators for the frame being built. Main thread only.
    private static final Map<RenderPass, Integer> drawCalls = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, Integer> vertices = new EnumMap<>(RenderPass.class);
    private static int uniformUploads;
    private static int chunksConsidered;
    private static int chunksDrawn;

    // The last frame that finished. Everything that reports reads these, never the accumulators
    // above, so a reader can never catch a half-built frame and wonder why opaque draws halved.
    private static final Map<RenderPass, Integer> lastDrawCalls = new EnumMap<>(RenderPass.class);
    private static final Map<RenderPass, Integer> lastVertices = new EnumMap<>(RenderPass.class);
    private static int lastUniformUploads;
    private static int lastChunksConsidered;
    private static int lastChunksDrawn;

    /*
     * Closes the frame just finished and opens the next one. Call once at the top of the game
     * loop, from the main thread.
     *
     */
    public static void beginFrame() {
        lastDrawCalls.clear();
        lastDrawCalls.putAll(drawCalls);
        lastVertices.clear();
        lastVertices.putAll(vertices);
        lastUniformUploads = uniformUploads;
        lastChunksConsidered = chunksConsidered;
        lastChunksDrawn = chunksDrawn;

        drawCalls.clear();
        vertices.clear();
        uniformUploads = 0;
        chunksConsidered = 0;
        chunksDrawn = 0;
    }

    public static void countDrawCall(RenderPass pass) {
        drawCalls.merge(pass, 1, Integer::sum);
    }

    public static void countVertices(RenderPass pass, int count) {
        vertices.merge(pass, count, Integer::sum);
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
        return lastDrawCalls.getOrDefault(pass, 0);
    }

    public static int vertices(RenderPass pass) {
        return lastVertices.getOrDefault(pass, 0);
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
        drawCalls.clear();
        lastDrawCalls.clear();
        vertices.clear();
        lastVertices.clear();
        uniformUploads = 0;
        chunksConsidered = 0;
        chunksDrawn = 0;
        lastUniformUploads = 0;
        lastChunksConsidered = 0;
        lastChunksDrawn = 0;
    }
}
