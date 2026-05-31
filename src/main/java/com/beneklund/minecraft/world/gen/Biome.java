package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.Color;

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
    // grassColor / foliageColor are the RGB multipliers applied to greyscale grass_top
    // and leaf textures in the Faithful pack. Values are approximate vanilla equivalents
    // per biome — tune them visually as needed.
    PLAINS(64, 15, new Color(0.57f, 0.74f, 0.35f, 1f), new Color(0.38f, 0.60f, 0.20f, 1f)),
    FOREST(64, 20, new Color(0.45f, 0.69f, 0.26f, 1f), new Color(0.28f, 0.51f, 0.13f, 1f)),
    MOUNTAINS(80, 50, new Color(0.60f, 0.72f, 0.42f, 1f), new Color(0.42f, 0.59f, 0.26f, 1f)),
    DESERT(63, 12, new Color(0.75f, 0.77f, 0.42f, 1f), new Color(0.70f, 0.73f, 0.37f, 1f)),
    OCEAN(48, 8, new Color(0.56f, 0.74f, 0.39f, 1f), new Color(0.37f, 0.61f, 0.23f, 1f));

    private final int baseHeight;
    private final int amplitude;
    private final Color grassColor;
    private final Color foliageColor;

    Biome(int baseHeight, int amplitude, Color grassColor, Color foliageColor) {
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.grassColor = grassColor;
        this.foliageColor = foliageColor;
    }

    public int getAmplitude() {
        return amplitude;
    }

    public int getBaseHeight() {
        return baseHeight;
    }

    // Used by ChunkMesher to tint greyscale grass_top and leaf textures.
    // ChunkMesher defaults to PLAINS until chunks carry per-block biome data.
    public Color grassColor() {
        return grassColor;
    }

    public Color foliageColor() {
        return foliageColor;
    }
}
