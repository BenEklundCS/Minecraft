package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import java.util.List;

/**
 * Data that parameterises {@link WorldGenerator}: noise layers, ores, trees, caves and the two
 * climate fields biomes are chosen from.
 *
 * <p>The interface is sealed so {@code WorldGenerator}'s constructor can sort a flat list of specs
 * by type. {@link #DEFAULT_WORLD_GENERATION} is the shipped world.
 */
public sealed interface IGenerationSpec {
    int BIOME_OCTAVES = 3;

    /**
     * Temperature varies over continental distances, humidity over roughly the distance to water.
     * The ratio {@code HUMIDITY_SCALE / TEMPERATURE_SCALE} is about 2.58, deliberately far from an
     * integer: an integer ratio lines the two fields' octaves up on each other, and the
     * coincidences read as grid structure in the biome map.
     */
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

    /**
     * One fBm layer of the height field, a component of {@link NoiseLayersSpec}. The sampled value
     * is multiplied by {@code weight}; {@code ridged} passes it through {@link NoiseHelper#ridge}.
     */
    record NoiseLayerSpec(
            int octaves, double scale, double persistence, double weight, long seedOffset, boolean ridged) {}

    /** The three weighted layers summed into the height field, named by role. */
    record NoiseLayersSpec(NoiseLayerSpec continental, NoiseLayerSpec erosion, NoiseLayerSpec detail)
            implements IGenerationSpec {}

    /** Replaces stone with {@code blockId} at each y in [{@code minY}, {@code maxY}] with probability {@code chance}. */
    record OreSpec(Block blockId, int minY, int maxY, float chance) implements IGenerationSpec {}

    /**
     * Places a tree on a grass column above sea level with probability {@code spawnChance}, when
     * at least {@code minHeadroom} blocks remain below the world ceiling.
     */
    record TreeSpec(float spawnChance, int minHeadroom) implements IGenerationSpec {}

    /** Carves air wherever 3D fBm exceeds {@code threshold}, from {@code minY} up; lower thresholds mean more cave. */
    record CaveSpec(double threshold, int octaves, double scale, double persistence, int minY, long seedOffset)
            implements IGenerationSpec {}

    /**
     * One climate field biomes are selected from: the first is temperature, the second humidity.
     * A single unweighted fBm sample, so it carries no weight or ridge flag.
     */
    record BiomeSpec(int octaves, double scale, double persistence, long seedOffset) implements IGenerationSpec {}
}
