package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.OpenSimplex2;

public class NoiseHelper {
    public double noise2(long seed, double x, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = scale;
        double maxAmplitude = 0;

        for (int i = 0; i < octaves; i++) {
            total += OpenSimplex2.noise2(seed, x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }

        return total / maxAmplitude;
    }

    public double noise3(long seed, double x, double y, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = scale;
        double maxAmplitude = 0;

        for (int i = 0; i < octaves; i++) {
            total += OpenSimplex2.noise3_ImproveXZ(seed, x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }

        return total / maxAmplitude;
    }
}
