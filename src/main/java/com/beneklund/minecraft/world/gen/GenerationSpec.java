package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import java.util.List;

public sealed interface GenerationSpec {
    public static List<GenerationSpec> DEFAULT_WORLD_GENERATION = List.of(
            new GenerationSpec.NoiseLayersSpec(
                    new GenerationSpec.NoiseLayerSpec(4, 0.002, 0.5, 0.5, 0),
                    new GenerationSpec.NoiseLayerSpec(3, 0.008, 0.5, 0.3, 100),
                    new GenerationSpec.NoiseLayerSpec(2, 0.04, 0.5, 0.2, 200)),
            new GenerationSpec.OreSpec(Block.COAL_ORE, 5, 50, 0.01f),
            new GenerationSpec.OreSpec(Block.IRON_ORE, 5, 30, 0.005f),
            new GenerationSpec.TreeSpec(0.05f, 8),
            new GenerationSpec.CaveSpec(0.6, 2, 0.04, 0.5, 5, 400),
            new GenerationSpec.BiomeSpec(1, 0.0005, 0.5, 300));

    // Component type — used inside NoiseLayersSpec, not a spec on its own.
    record NoiseLayerSpec(int octaves, double scale, double persistence, double weight, long seedOffset) {}

    // The three terrain-blend layers, named so their roles are unambiguous at the call site.
    record NoiseLayersSpec(NoiseLayerSpec continental, NoiseLayerSpec erosion, NoiseLayerSpec detail)
            implements GenerationSpec {}

    record OreSpec(byte blockId, int minY, int maxY, float chance) implements GenerationSpec {}

    record TreeSpec(float spawnChance, int minHeadroom) implements GenerationSpec {}

    record CaveSpec(double threshold, int octaves, double scale, double persistence, int minY, long seedOffset)
            implements GenerationSpec {}

    // Controls the low-frequency noise that selects biomes. Kept separate from
    // NoiseLayerSpec because biome noise isn't weighted or blended — it's a single sample.
    record BiomeSpec(int octaves, double scale, double persistence, long seedOffset) implements GenerationSpec {}
}
