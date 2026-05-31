package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Pure factory: same (ChunkPos, seed) always produces the same Chunk.
// No mutable state — safe to call from multiple worker threads in parallel.
//
// Pipeline per chunk:
//   1. terrain   — fills every column with the correct block stack
//   2. ores      — scatters coal/iron inside the stone layer
//   3. trees     — places oak trees on eligible grass columns
//   4. caves     — punches air into the terrain with 3D noise
//
// Passes run in this order so later passes can read what earlier passes wrote
// (e.g. placeTrees checks that the surface block is GRASS, not raw stone).
public class WorldGenerator implements IWorldGenerator {

    private static final int SEA_LEVEL = 62;
    private static final int MIN_SURFACE_Y = 4;
    private static final int MAX_SURFACE_Y = 250;
    private static final int DIRT_DEPTH = 2;
    private static final long COL_SEED_PRIME_X = 341873128712L;
    private static final long COL_SEED_PRIME_Z = 132897987541L;

    private final BlockRegistry registry;
    private final NoiseHelper noiseHelper;
    private final TreePlacer treePlacer;
    private final List<IGenerationSpec.OreSpecI> oreSpecs;
    private final IGenerationSpec.TreeSpecI treeSpec;
    private final IGenerationSpec.CaveSpecI caveSpec;
    private final IGenerationSpec.NoiseLayersSpecI noiseLayers;
    private final IGenerationSpec.BiomeSpecI biomeSpec;

    // Convenience constructor for tests, uses vanilla-defaults.
    public WorldGenerator(BlockRegistry registry) {
        this(registry, IGenerationSpec.DEFAULT_WORLD_GENERATION);
    }

    public WorldGenerator(BlockRegistry registry, List<IGenerationSpec> specs) {
        this.registry = registry;
        this.noiseHelper = new NoiseHelper();
        this.treePlacer = new TreePlacer();

        List<IGenerationSpec.OreSpecI> ores = new ArrayList<>();
        IGenerationSpec.NoiseLayersSpecI layers = null;
        IGenerationSpec.TreeSpecI tree = null;
        IGenerationSpec.CaveSpecI cave = null;
        IGenerationSpec.BiomeSpecI biome = null;
        for (IGenerationSpec spec : specs) {
            if (spec instanceof IGenerationSpec.OreSpecI ore) ores.add(ore);
            else if (spec instanceof IGenerationSpec.NoiseLayersSpecI n) layers = n;
            else if (spec instanceof IGenerationSpec.TreeSpecI t) tree = t;
            else if (spec instanceof IGenerationSpec.CaveSpecI c) cave = c;
            else if (spec instanceof IGenerationSpec.BiomeSpecI b) biome = b;
        }
        this.oreSpecs = ores;
        this.noiseLayers = layers;
        this.treeSpec = tree;
        this.caveSpec = cave;
        this.biomeSpec = biome;
    }

    @Override
    public void generate(ChunkPos pos, long seed, Chunk chunk) {
        int[] surfaceHeights = new int[Chunk.SIZE_XZ * Chunk.SIZE_XZ];

        for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;

                int surfaceY = computeSurfaceY(seed, worldX, worldZ);
                surfaceHeights[localX + localZ * Chunk.SIZE_XZ] = surfaceY;
                fillColumn(chunk, localX, localZ, surfaceY);
                placeOres(chunk, seed, worldX, worldZ, localX, localZ);
            }
        }

        placeTrees(chunk, seed, pos, surfaceHeights);
        carveCaves(chunk, seed, pos);
    }

    private int computeSurfaceY(long seed, int worldX, int worldZ) {
        double raw = sampleLayer(seed, worldX, worldZ, noiseLayers.continental())
                + sampleLayer(seed, worldX, worldZ, noiseLayers.erosion())
                + sampleLayer(seed, worldX, worldZ, noiseLayers.detail());
        Biome biome = selectBiome(sampleSpec(seed, worldX, worldZ, biomeSpec));
        return Math.clamp((int) (biome.getBaseHeight() + raw * biome.getAmplitude()), MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    // Adjacent Biome ordinals are geographically adjacent in-world because biomeNoise
    // changes slowly — so the linear mapping produces wide, gradual transitions.
    private Biome selectBiome(double noise) {
        Biome[] biomes = Biome.values();
        int index = Math.clamp((int) (noiseHelper.normalize(noise) * biomes.length), 0, biomes.length - 1);
        return biomes[index];
    }

    private double sampleLayer(long seed, int x, int z, IGenerationSpec.NoiseLayerSpec layer) {
        return noiseHelper.noise2(seed + layer.seedOffset(), x, z, layer.octaves(), layer.persistence(), layer.scale())
                * layer.weight();
    }

    private double sampleSpec(long seed, int x, int z, IGenerationSpec.BiomeSpecI spec) {
        return noiseHelper.noise2(seed + spec.seedOffset(), x, z, spec.octaves(), spec.persistence(), spec.scale());
    }

    private void fillColumn(Chunk chunk, int localX, int localZ, int surfaceY) {
        int stoneTop = surfaceY - DIRT_DEPTH - 1;
        int dirtBottom = surfaceY - DIRT_DEPTH;
        int dirtTop = surfaceY - 1;
        int waterStart = surfaceY + 1;
        byte surfaceBlock = surfaceY > SEA_LEVEL ? Block.GRASS : Block.SAND;

        chunk.setBlock(localX, 0, localZ, Block.BEDROCK);

        for (int y = 1; y <= stoneTop; y++) {
            chunk.setBlock(localX, y, localZ, Block.STONE);
        }

        chunk.setBlock(localX, dirtBottom, localZ, Block.DIRT);
        chunk.setBlock(localX, dirtTop, localZ, Block.DIRT);
        chunk.setBlock(localX, surfaceY, localZ, surfaceBlock);

        for (int y = waterStart; y <= SEA_LEVEL; y++) {
            chunk.setBlock(localX, y, localZ, Block.WATER);
        }
    }

    // Per-column seed derived by XOR-mixing world seed with world coords using large primes.
    // Changing X or Z by even 1 block produces a completely different colSeed, so ore
    // placement is uncorrelated between columns. Same formula is used in placeTrees so
    // the two passes share the same per-column identity without sharing a Random instance.
    private void placeOres(Chunk chunk, long seed, int worldX, int worldZ, int localX, int localZ) {
        long colSeed = seed ^ ((long) worldX * COL_SEED_PRIME_X) ^ ((long) worldZ * COL_SEED_PRIME_Z);
        Random colRng = new Random(colSeed);

        for (IGenerationSpec.OreSpecI ore : oreSpecs) {
            for (int y = ore.minY(); y <= ore.maxY(); y++) {
                if (chunk.getBlock(localX, y, localZ) == Block.STONE && colRng.nextFloat() < ore.chance()) {
                    chunk.setBlock(localX, y, localZ, ore.blockId());
                }
            }
        }
    }

    // surfaceHeights is passed in rather than recomputed here because computeSurfaceY
    // is moderately expensive (multiple octave loops) and terrain already computed it
    // for every column during fillColumn. Reusing the array avoids 256 redundant calls.
    private void placeTrees(Chunk chunk, long seed, ChunkPos pos, int[] surfaceHeights) {
        for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                int surfaceY = surfaceHeights[localX + localZ * Chunk.SIZE_XZ];

                if (chunk.getBlock(localX, surfaceY, localZ) != Block.GRASS) continue;
                if (surfaceY <= SEA_LEVEL) continue;
                if (treeSpec == null || surfaceY + treeSpec.minHeadroom() >= Chunk.SIZE_Y) continue;

                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;
                long colSeed = seed ^ ((long) worldX * COL_SEED_PRIME_X) ^ ((long) worldZ * COL_SEED_PRIME_Z);
                if (new Random(colSeed).nextFloat() >= treeSpec.spawnChance()) continue;

                treePlacer.placeTree(chunk, localX, surfaceY, localZ);
            }
        }
    }

    // Lower caveSpec.threshold() to get denser cave systems; raise it for sparse/rare caves.
    private void carveCaves(Chunk chunk, long seed, ChunkPos pos) {
        if (caveSpec == null) return;
        for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;

                for (int y = caveSpec.minY(); y < Chunk.SIZE_Y; y++) {
                    if (chunk.getBlock(localX, y, localZ) == Block.BEDROCK) continue;
                    double caveNoise = noiseHelper.noise3(
                            seed + caveSpec.seedOffset(),
                            worldX,
                            y,
                            worldZ,
                            caveSpec.octaves(),
                            caveSpec.persistence(),
                            caveSpec.scale());
                    if (caveNoise > caveSpec.threshold()) {
                        chunk.setBlock(localX, y, localZ, Block.AIR);
                    }
                }
            }
        }
    }
}
