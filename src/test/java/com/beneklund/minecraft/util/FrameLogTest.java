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
}
