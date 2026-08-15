package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import java.util.List;

public sealed interface IGenerationSpec {
    int BIOME_OCTAVES = 3;

    // Temperature varies over continental distances, humidity over the distance to water.
    // Ratio 0.00090 / 0.00035 = 2.57, deliberately not 2 or 4: integer ratios stack the two
    // fields' octaves on top of each other and the coincidences read as grid structure.
    double TEMPERATURE_SCALE = 0.00012; // ~8300 block features
    double HUMIDITY_SCALE = 0.00031; // ~3200 block features

    List<IGenerationSpec> DEFAULT_WORLD_GENERATION = List.of(
            new NoiseLayersSpec(
                    new IGenerationSpec.NoiseLayerSpec(4, 0.002, 0.5, 0.5, 0, true),
                    new IGenerationSpec.NoiseLayerSpec(3, 0.008, 0.5, 0.3, 100, true),
                    new IGenerationSpec.NoiseLayerSpec(2, 0.04, 0.5, 0.2, 200, false)),
            new OreSpec(Block.COAL_ORE, 5, 50, 0.01f),
            new OreSpec(Block.IRON_ORE, 5, 30, 0.005f),
            new TreeSpec(0.05f, 8),
            new CaveSpec(0.6, 2, 0.04, 0.5, 5, 400),
            new BiomeSpec(BIOME_OCTAVES, TEMPERATURE_SCALE, 0.5, 300), // temperature
            new BiomeSpec(BIOME_OCTAVES, HUMIDITY_SCALE, 0.5, 700)); // humidity

    // Component type — used inside NoiseLayersSpec, not a spec on its own.
    record NoiseLayerSpec(
            int octaves, double scale, double persistence, double weight, long seedOffset, boolean ridged) {}

    // The three terrain-blend layers, named so their roles are unambiguous at the call site.
    record NoiseLayersSpec(NoiseLayerSpec continental, NoiseLayerSpec erosion, NoiseLayerSpec detail)
            implements IGenerationSpec {}

    record OreSpec(Block blockId, int minY, int maxY, float chance) implements IGenerationSpec {}

    record TreeSpec(float spawnChance, int minHeadroom) implements IGenerationSpec {}

    record CaveSpec(double threshold, int octaves, double scale, double persistence, int minY, long seedOffset)
            implements IGenerationSpec {}

    // Controls the low-frequency noise that selects biomes. Kept separate from
    // NoiseLayerSpec because biome noise isn't weighted or blended — it's a single sample.
    record BiomeSpec(int octaves, double scale, double persistence, long seedOffset) implements IGenerationSpec {}
}
