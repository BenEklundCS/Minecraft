package com.beneklund.minecraft.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NoiseHelperTest {

    private final NoiseHelper noise = new NoiseHelper();

    @Test
    void noise2_outputInRange() {
        long seed = 12345L;
        for (int i = 0; i < 1000; i++) {
            double x = (i * 17.3) - 500;
            double z = (i * 31.7) - 500;
            double v = noise.noise2(seed, x, z, 4, 0.5, 0.01);
            assertTrue(v >= -1.0 && v <= 1.0, "noise2 out of range: " + v + " at x=" + x + " z=" + z);
        }
    }

    @Test
    void noise2_sameSeedAndCoords_sameOutput() {
        long seed = 99L;
        double a = noise.noise2(seed, 100.0, 200.0, 3, 0.5, 0.01);
        double b = noise.noise2(seed, 100.0, 200.0, 3, 0.5, 0.01);
        assertEquals(a, b);
    }

    @Test
    void noise3_outputInRange() {
        long seed = 54321L;
        for (int i = 0; i < 500; i++) {
            double x = i * 13.1;
            double y = i * 7.7;
            double z = i * 19.3;
            double v = noise.noise3(seed, x, y, z, 2, 0.5, 0.04);
            assertTrue(v >= -1.0 && v <= 1.0, "noise3 out of range: " + v + " at (" + x + "," + y + "," + z + ")");
        }
    }

    @Test
    void noise3_sameSeedAndCoords_sameOutput() {
        long seed = 77L;
        double a = noise.noise3(seed, 10.0, 64.0, 20.0, 2, 0.5, 0.04);
        double b = noise.noise3(seed, 10.0, 64.0, 20.0, 2, 0.5, 0.04);
        assertEquals(a, b);
    }
}
