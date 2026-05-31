package com.beneklund.minecraft.world.gen;

// Each biome shapes how computeSurfaceY() maps raw noise [-1,1] to a world Y:
//   finalY = clamp(baseHeight + raw * amplitude, 4, 250)
//
// baseHeight is the Y the surface lands at when raw=0 (flat noise).
// amplitude stretches how much the terrain varies around that base.
// Sea level is 62 — bases above it produce dry land, bases below produce ocean.
//
// Biome is selected from a very-low-frequency noise sample (scale=0.0005) so the
// transition zones are hundreds of blocks wide and the patterns don't correlate
// with the terrain detail noise (which uses seed offsets 0, 100, 200).
// ordinal() order matters: the noise value is mapped linearly across the array,
// so adjacent biomes in this list will also be geographically adjacent in-world.
public enum Biome {
    PLAINS(64, 15),
    FOREST(64, 20), // same base as plains but more vertical variation
    MOUNTAINS(80, 50), // high base + large amplitude → dramatic peaks
    DESERT(63, 12), // slightly below sea level base, low variation → flat sandy lowlands
    OCEAN(48, 8); // base 14 blocks below sea level → always submerged

    private final int baseHeight;
    private final int amplitude;

    Biome(int baseHeight, int amplitude) {
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
    }

    public int getAmplitude() {
        return this.amplitude;
    }

    public int getBaseHeight() {
        return this.baseHeight;
    }
}
