package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.Color;

/**
 * The terrain shape and tints of a biome, or of a blend of two at a border.
 *
 * @param baseHeight surface Y where the height noise is zero
 * @param amplitude how far the height noise moves the surface from {@code baseHeight}
 */
public record TerrainProfile(int baseHeight, int amplitude, Color grassColor, Color foliageColor) {}
