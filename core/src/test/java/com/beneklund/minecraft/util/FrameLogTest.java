package com.beneklund.minecraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class FrameLogTest {
    @Test
    void ringWrapsAtCapacity() {
        float ms = 0.0f;
        int capacity = 4;
        FrameLog log = new FrameLog(capacity);
        log.record(ms);
        log.record(ms);
        log.record(ms);
        log.record(ms);
        log.record(ms);
        log.record(ms);
        assertEquals(capacity, log.count());
    }

    // Case A: a skewed distribution. 59 fast frames and one 200ms stall. The whole point of
    // percentiles over a mean is that p50 stays on the fast frames and p99 lands on the stall.
    // A mean-returning implementation answers 16.7 for both and fails here.
    @Test
    void percentilesMatchHandWorkedIndices() {
        FrameLog log = new FrameLog(300);
        for (int i = 0; i < 59; i++) {
            log.record(13.3f);
        }
        log.record(200.0f);

        assertEquals(13.3f, log.percentile(FrameLog.P50), 0.01f);
        assertEquals(200.0f, log.percentile(FrameLog.P99), 0.01f);
        assertEquals(200.0f, log.max(), 0.01f);
    }

    // Case B: the degenerate distribution. Every sample identical, so every percentile and the
    // max must all be that value — no interpolation, no off-by-one drift.
    @Test
    void uniformSamplesReportTheSameValueEverywhere() {
        FrameLog log = new FrameLog(300);
        for (int i = 0; i < 60; i++) {
            log.record(16.4f);
        }

        assertEquals(16.4f, log.percentile(FrameLog.P50), 0.01f);
        assertEquals(16.4f, log.percentile(FrameLog.P99), 0.01f);
        assertEquals(16.4f, log.max(), 0.01f);
    }

    @Test
    void handlesEdges() {
        FrameLog log = new FrameLog(300);
        for (int i = 0; i < 60; i++) {
            log.record(17.2f);
        }
        assertEquals(17.2f, log.percentile(FrameLog.P0), 0.01f);
        assertEquals(17.2f, log.percentile(FrameLog.P100), 0.01f);
    }

    @Test
    void handlesEmpty() {
        FrameLog log = new FrameLog(300);
        assertEquals(0, log.count());
        assertEquals(0, log.percentile(FrameLog.P50));
    }
}
