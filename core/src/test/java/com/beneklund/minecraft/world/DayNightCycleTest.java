package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

public class DayNightCycleTest {
    @Test
    void dayNightCycle_Midnight() {
        DayNightCycle midnight = cycle();
        assertEquals(DayNightCycle.NIGHT_BRIGHTNESS, midnight.skyBrightness());
    }

    @Test
    void dayNightCycle_Noon() {
        DayNightCycle noon = cycleAt(DayNightCycle.NOON);
        assertEquals(DayNightCycle.DAY_BRIGHTNESS, noon.skyBrightness());
    }

    @Test
    void dayNightCycle_Other() {
        float time = 0.25f;
        float expect = 0.575f;
        DayNightCycle c = cycleAt(time);
        assertEquals(expect, c.skyBrightness());
    }

    @Test
    void dayNightCycle_Advance() {
        DayNightCycle def = cycle();
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS / 2);
        assertEquals(DayNightCycle.NOON, def.timeOfDay());
        assertEquals(DayNightCycle.DAY_BRIGHTNESS, def.skyBrightness());
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS);
        assertEquals(DayNightCycle.NOON, def.timeOfDay());
        def.advance(DayNightCycle.DEFAULT_DAY_SECONDS / 2);
        assertEquals(DayNightCycle.MIDNIGHT, def.timeOfDay());
        assertEquals(DayNightCycle.NIGHT_BRIGHTNESS, def.skyBrightness());
    }

    @Test
    void sunDirection_tracksTimeOfDay() {
        assertVec(0, 1, 0, cycleAt(DayNightCycle.NOON).sunDirection());
        assertVec(0, -1, 0, cycleAt(DayNightCycle.MIDNIGHT).sunDirection());
        assertEquals(0.0f, cycleAt(0.25f).sunDirection().y, 1e-5); // sunrise: on the horizon
        assertEquals(0.0f, cycleAt(0.75f).sunDirection().y, 1e-5); // sunset
    }

    private void assertVec(float x, float y, float z, Vector3f vector3f) {
        assertEquals(x, vector3f.x, 1e-5);
        assertEquals(y, vector3f.y, 1e-5);
        assertEquals(z, vector3f.z, 1e-5);
    }

    private DayNightCycle cycleAt(float time) {
        return new DayNightCycle(time, DayNightCycle.DEFAULT_DAY_SECONDS);
    }

    private DayNightCycle cycle() {
        return new DayNightCycle(DayNightCycle.MIDNIGHT, DayNightCycle.DEFAULT_DAY_SECONDS);
    }
}
