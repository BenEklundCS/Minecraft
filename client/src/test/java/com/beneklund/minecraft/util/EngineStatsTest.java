package com.beneklund.minecraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.renderer.RenderPass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EngineStatsTest {

    // The counters are static and survive between test classes in one JVM, so every case here
    // has to start from a known state or it inherits whatever ran before it.
    @BeforeEach
    void clear() {
        EngineStats.reset();
    }

    /*
     * Vertices add up within a pass and stay separate between passes.
     *
     * An implementation that keeps one global total answers 250 to both questions. One that
     * stores the latest value instead of accumulating answers 100 to the first. Both are easy
     * mistakes and both would make the shadow pass look cheaper than it is, which is the exact
     * number this counter exists to get right.
     */
    @Test
    void verticesAccumulatePerPass() {
        EngineStats.countVertices(RenderPass.OPAQUE, 100);
        EngineStats.countVertices(RenderPass.OPAQUE, 100);
        EngineStats.countVertices(RenderPass.SHADOW, 50);
        EngineStats.beginFrame();

        assertEquals(200, EngineStats.vertices(RenderPass.OPAQUE));
        assertEquals(50, EngineStats.vertices(RenderPass.SHADOW));
    }

    // A pass nothing was submitted to reads 0 rather than throwing. The stats snapshot asks for
    // every pass every frame, including ones that drew nothing.
    @Test
    void unusedPassReadsZero() {
        EngineStats.countVertices(RenderPass.OPAQUE, 7);
        EngineStats.beginFrame();

        assertEquals(0, EngineStats.vertices(RenderPass.TRANSPARENT));
    }

    /*
     * beginFrame closes the frame just built, so a reader never sees a half-finished one.
     *
     * Before the swap the counter still reports the previous frame; after it, the new one. An
     * implementation that read the live accumulator would report 300 in the middle of the
     * assertion below and make every per-frame number depend on when it was asked for.
     */
    @Test
    void verticesReportThePreviousFrameUntilItCloses() {
        EngineStats.countVertices(RenderPass.OPAQUE, 200);
        EngineStats.beginFrame();
        EngineStats.countVertices(RenderPass.OPAQUE, 300);

        assertEquals(200, EngineStats.vertices(RenderPass.OPAQUE));
        EngineStats.beginFrame();
        assertEquals(300, EngineStats.vertices(RenderPass.OPAQUE));
    }
}
