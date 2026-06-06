package com.beneklund.minecraft.world.gen;

// Pairs the dominant Biome enum (used for block-type decisions) with blended TerrainProfile
// (used for terrain height math). The dominant biome is whichever of the two neighbouring
// biomes the noise sample falls closest to.
record ResolvedBiome(Biome type, TerrainProfile data) {}
