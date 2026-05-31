package com.beneklund.minecraft.world.gen;

public enum Biome {
    PLAINS(64, 15),
    FOREST(64, 20),
    MOUNTAINS(80, 50),
    DESERT(63, 12),
    OCEAN(48, 8); // base well below sea level (62) so columns are underwater

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
