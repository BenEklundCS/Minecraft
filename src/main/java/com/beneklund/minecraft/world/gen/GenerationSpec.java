package com.beneklund.minecraft.world.gen;

public sealed interface GenerationSpec {
    // Component type — used inside NoiseLayersSpec, not a spec on its own.
    record NoiseLayerSpec(int octaves, double scale, double persistence, double weight, long seedOffset) {}

    // The three terrain-blend layers, named so their roles are unambiguous at the call site.
    record NoiseLayersSpec(NoiseLayerSpec continental, NoiseLayerSpec erosion, NoiseLayerSpec detail)
            implements GenerationSpec {}

    record OreSpec(byte blockId, int minY, int maxY, float chance) implements GenerationSpec {}

    record TreeSpec(float spawnChance, int minHeadroom) implements GenerationSpec {}

    record CaveSpec(double threshold, int octaves, double scale, double persistence, int minY)
            implements GenerationSpec {}
}
