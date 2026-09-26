package com.beneklund.minecraft.world.gen;

/**
 * The biome at one column: the nearest {@link Biome} in climate space, used for block choices,
 * and a {@link TerrainProfile} blended between the nearest two, used for height and tint.
 */
record ResolvedBiome(Biome type, TerrainProfile data) {}
