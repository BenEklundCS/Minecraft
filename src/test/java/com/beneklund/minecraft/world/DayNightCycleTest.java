package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DayNightCycleTest {
    @Test
    void dayNightCycle_Midnight() {
        DayNightCycle midnight = ofMidnight();
        assertEquals(0.15f, midnight.skyBrightness());
    }

    @Test
    void dayNightCycle_Noon() {
        DayNightCycle noon = ofNoon();
        assertEquals(1.0f, noon.skyBrightness());
    }

    private DayNightCycle ofNoon() {
        return new DayNightCycle(DayNightCycle.NOON);
    }

    private DayNightCycle ofMidnight() {
        return new DayNightCycle(DayNightCycle.MIDNIGHT);
    }
}
