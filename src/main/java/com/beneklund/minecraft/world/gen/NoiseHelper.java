package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.OpenSimplex2;

// https://en.wikipedia.org/wiki/Fractional_Brownian_motion
// Fractal (octave) noise on top of OpenSimplex2.
//
// One noise sample gives a smooth blobby field. "Octaves" layer multiple samples
// at progressively finer frequencies to add detail: coarse shapes from the first
// octave, medium hills from the second, rocky bumps from the third, etc.
// Each finer octave contributes less (amplitude shrinks by persistence each pass).
// Dividing by maxAmplitude at the end normalises the result to [-1, 1] regardless
// of how many octaves are used.
//
// Typical call sites in WorldGenerator:
//   scale=0.002 → continental (changes slowly over thousands of blocks)
//   scale=0.008 → erosion/hilliness
//   scale=0.04  → fine surface detail or cave carving
public class NoiseHelper {

    // 2D fractal noise — use for surface height maps.
    // frequency doubles each octave so each pass samples 2× finer detail.
    // persistence=0.5 means each octave contributes half as much as the previous.
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

    // 3D fractal noise — use for volumetric features like caves.
    // noise3_ImproveXZ is an OpenSimplex2 variant optimised for terrain: it orients
    // the noise lattice so the XZ plane has higher isotropy than a naive 3D grid,
    // avoiding the vertical-stripe artefacts you'd otherwise get in cave systems.
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

    public double ridged2(long seed, double x, double z, int octaves, double persistence, double scale) {
        return noise2(seed, x, z, octaves, persistence, scale);
    }

    // Shift the [-1, 1] output range to [0, 1]. The +1 moves the floor to 0,
    // the /2 compresses the resulting [0, 2] back to unit width.
    public double normalize(double noise) {
        return (noise + 1.0) / 2.0;
    }
}
