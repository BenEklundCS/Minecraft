package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.Color;

/**
 * A biome: an anchor point in climate space plus the terrain shape and tints it contributes.
 *
 * <p>The generator samples temperature and humidity noise at each column, normalised to {@code [0,
 * 1]}, and picks the biome whose {@code (temperature, humidity)} anchor is nearest, with a small
 * per-biome noise jitter on the distance so borders come out ragged. It then blends the nearest
 * two biomes' {@link TerrainProfile}s by relative distance, so height and colour change smoothly
 * across a border while the dominant biome still decides block types.
 *
 * <p>The surface height is {@code baseHeight + noise * amplitude}, clamped to the world. Sea level
 * is 62, so a base above it tends to dry land and a base below it to ocean.
 */
public enum Biome {
    PLAINS(
            0.5f,
            0.5f,
            new TerrainProfile(64, 15, new Color(0.57f, 0.74f, 0.35f, 1f), new Color(0.38f, 0.60f, 0.20f, 1f))),
    FOREST(
            0.5f,
            0.75f,
            new TerrainProfile(64, 20, new Color(0.45f, 0.69f, 0.26f, 1f), new Color(0.28f, 0.51f, 0.13f, 1f))),
    MOUNTAINS(
            0.1f,
            0.25f,
            new TerrainProfile(95, 90, new Color(0.60f, 0.72f, 0.42f, 1f), new Color(0.42f, 0.59f, 0.26f, 1f))),
    DESERT(
            1.0f,
            0.0f,
            new TerrainProfile(63, 12, new Color(0.75f, 0.77f, 0.42f, 1f), new Color(0.70f, 0.73f, 0.37f, 1f))),
    OCEAN(
            0.2f,
            1.0f,
            new TerrainProfile(48, 8, new Color(0.56f, 0.74f, 0.39f, 1f), new Color(0.37f, 0.61f, 0.23f, 1f)));

    private final TerrainProfile data;
    private final float temperature;
    private final float humidity;

    Biome(float temperature, float humidity, TerrainProfile data) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.data = data;
    }

    public int getAmplitude() {
        return data.amplitude();
    }

    public int getBaseHeight() {
        return data.baseHeight();
    }

    public float getTemperature() {
        return temperature;
    }

    public float getHumidity() {
        return humidity;
    }

    /**
     * Tint for the greyscale grass textures. The mesher tints everything with {@link #PLAINS} until
     * chunks carry per-column biome data.
     */
    public Color grassColor() {
        return data.grassColor();
    }

    /** Tint for the greyscale leaf textures. */
    public Color foliageColor() {
        return data.foliageColor();
    }
}
