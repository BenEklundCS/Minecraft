package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DayNightCycleTest {
    @Test
    void dayNightCycle_Midnight() {
        DayNightCycle midnight = ofDefault();
        assertEquals(DayNightCycle.NIGHT_BRIGHTNESS, midnight.skyBrightness());
    }

    @Test
    void dayNightCycle_Noon() {
        DayNightCycle noon = ofDefaultLength(DayNightCycle.NOON);
        assertEquals(DayNightCycle.DAY_BRIGHTNESS, noon.skyBrightness());
    }

    @Test
    void dayNightCycle_Other() {
        float time = 0.25f;
        float expect = 0.575f;
        DayNightCycle c = ofDefaultLength(time);
        assertEquals(expect, c.skyBrightness());
    }

    @Test
    void dayNightCycle_Advance() {
        DayNightCycle def = ofDefault();
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS / 2);
        assertEquals(DayNightCycle.NOON, def.timeOfDay());
        assertEquals(DayNightCycle.DAY_BRIGHTNESS, def.skyBrightness());
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS);
        assertEquals(DayNightCycle.NOON, def.timeOfDay());
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS / 2);
        assertEquals(DayNightCycle.MIDNIGHT, def.timeOfDay());
        assertEquals(DayNightCycle.NIGHT_BRIGHTNESS, def.skyBrightness());
    }

    private DayNightCycle ofDefaultLength(float time) {
        return new DayNightCycle(time, DayNightCycle.DEFAULT_DAY_SECONDS);
    }

    private DayNightCycle ofDefault() {
        return new DayNightCycle(DayNightCycle.MIDNIGHT, DayNightCycle.DEFAULT_DAY_SECONDS);
    }
}
