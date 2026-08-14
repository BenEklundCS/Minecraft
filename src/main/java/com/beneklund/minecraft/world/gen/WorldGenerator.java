package com.beneklund.minecraft.world.gen;

import static org.joml.Math.lerp;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.*;

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
    private static final int SNOW_LINE = 95;
    private static final long COL_SEED_PRIME_X = 341873128712L;
    private static final long COL_SEED_PRIME_Z = 132897987541L;
    public static final double WARP_STRENGTH = 40.0;
    public static final double WARP_SCALE = 0.0008;
    private static final long WARP_SEED_X = 7001L;
    private static final long WARP_SEED_Z = 7002L;

    private final BlockRegistry registry;
    private final NoiseHelper noiseHelper;
    private final TreePlacer treePlacer;
    private final List<IGenerationSpec.OreSpec> oreSpecs;
    private final IGenerationSpec.TreeSpec treeSpec;
    private final IGenerationSpec.CaveSpec caveSpec;
    private final IGenerationSpec.NoiseLayersSpec noiseLayers;
    private final IGenerationSpec.BiomeSpec tempSpec;
    private final IGenerationSpec.BiomeSpec humidSpec;

    // Convenience constructor for tests, uses vanilla-defaults.
    public WorldGenerator(BlockRegistry registry) {
        this(registry, IGenerationSpec.DEFAULT_WORLD_GENERATION);
    }

    public WorldGenerator(BlockRegistry registry, List<IGenerationSpec> specs) {
        this.registry = registry;
        noiseHelper = new NoiseHelper();
        treePlacer = new TreePlacer();

        List<IGenerationSpec.OreSpec> ores = new ArrayList<>();
        IGenerationSpec.NoiseLayersSpec layers = null;
        IGenerationSpec.TreeSpec tree = null;
        IGenerationSpec.CaveSpec cave = null;
        List<IGenerationSpec.BiomeSpec> biomeSpecs = new ArrayList<>();
        for (IGenerationSpec spec : specs) {
            if (spec instanceof IGenerationSpec.OreSpec ore) ores.add(ore);
            else if (spec instanceof IGenerationSpec.NoiseLayersSpec n) layers = n;
            else if (spec instanceof IGenerationSpec.TreeSpec t) tree = t;
            else if (spec instanceof IGenerationSpec.CaveSpec c) cave = c;
            else if (spec instanceof IGenerationSpec.BiomeSpec b) biomeSpecs.add(b);
        }
        oreSpecs = ores;
        noiseLayers = layers;
        treeSpec = tree;
        caveSpec = cave;
        tempSpec = biomeSpecs.size() > 0 ? biomeSpecs.get(0) : null;
        humidSpec = biomeSpecs.size() > 1 ? biomeSpecs.get(1) : null;
    }

    @Override
    public void generate(ChunkPos pos, long seed, Chunk chunk) {
        int[] surfaceHeights = new int[Chunk.SIZE_XZ * Chunk.SIZE_XZ];

        for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;

                ResolvedBiome biome = selectBiome(
                        sampleSpec(seed, worldX, worldZ, tempSpec), sampleSpec(seed, worldX, worldZ, humidSpec));
                int surfaceY = computeSurfaceY(seed, worldX, worldZ, biome.data());
                surfaceHeights[localX + localZ * Chunk.SIZE_XZ] = surfaceY;
                fillColumn(chunk, localX, localZ, surfaceY, biome.type());
                placeOres(chunk, seed, worldX, worldZ, localX, localZ);
            }
        }

        placeTrees(chunk, seed, pos, surfaceHeights);
        carveCaves(chunk, seed, pos);
    }

    private double warpX(long seed, double x, double z, double strength) {
        return x + strength * noiseHelper.noise2(seed + WARP_SEED_X, x, z, 2, 0.5, WARP_SCALE);
    }

    private double warpZ(long seed, double x, double z, double strength) {
        return z + strength * noiseHelper.noise2(seed + WARP_SEED_Z, x, z, 2, 0.5, WARP_SCALE);
    }

    private int computeSurfaceY(long seed, int worldX, int worldZ, TerrainProfile biome) {
        double wx = warpX(seed, worldX, worldZ, WARP_STRENGTH);
        double wz = warpZ(seed, worldX, worldZ, WARP_STRENGTH);

        double raw = sampleLayer(seed, wx, wz, noiseLayers.continental())
                + sampleLayer(seed, wx, wz, noiseLayers.erosion())
                + sampleLayer(seed, wx, wz, noiseLayers.detail());
        return Math.clamp((int) (biome.baseHeight() + raw * biome.amplitude()), MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    // Adjacent Biome ordinals are geographically adjacent in-world because biomeNoise
    // changes slowly — so the linear mapping produces wide, gradual transitions.
    // dominant is whichever neighbour the noise sample falls closer to, so callers can
    // make block-identity decisions (surface block type, tree species, etc.) on a clean enum.
    private ResolvedBiome selectBiome(double tempNoise, double humidNoise) {
        Biome[] biomes = Biome.values();
        double temp = noiseHelper.normalize(tempNoise);
        double humid = noiseHelper.normalize(humidNoise);

        Biome best = biomes[0];
        double bestDist = Double.MAX_VALUE;
        Biome second = biomes[0];
        double secondDist = Double.MAX_VALUE;
        for (Biome b : biomes) {
            double d = Math.pow(b.getTemperature() - temp, 2) + Math.pow(b.getHumidity() - humid, 2);
            if (d < bestDist) {
                second = best;
                secondDist = bestDist;
                best = b;
                bestDist = d;
            } else if (d < secondDist) {
                second = b;
                secondDist = d;
            }
        }

        // t=0 means fully best, t=0.5 means equidistant between the two closest biomes
        double t = bestDist / (bestDist + secondDist);
        int baseHeight = (int) lerp(best.getBaseHeight(), second.getBaseHeight(), t);
        int amplitude = (int) lerp(best.getAmplitude(), second.getAmplitude(), t);
        Color grassColor = Color.lerp(best.grassColor(), second.grassColor(), t);
        Color foliageColor = Color.lerp(best.foliageColor(), second.foliageColor(), t);
        return new ResolvedBiome(best, new TerrainProfile(baseHeight, amplitude, grassColor, foliageColor));
    }

    private double sampleLayer(long seed, double x, double z, IGenerationSpec.NoiseLayerSpec layer) {
        double n = layer.ridged()
                ? noiseHelper.ridged2(
                        seed + layer.seedOffset(), x, z, layer.octaves(), layer.persistence(), layer.scale())
                : noiseHelper.noise2(
                        seed + layer.seedOffset(), x, z, layer.octaves(), layer.persistence(), layer.scale());
        return n * layer.weight();
    }

    private double sampleSpec(long seed, int x, int z, IGenerationSpec.BiomeSpec spec) {
        return noiseHelper.noise2(seed + spec.seedOffset(), x, z, spec.octaves(), spec.persistence(), spec.scale());
    }

    private record BiomeColumnBlocks(Block surface, Block subsurface, Block depth) {}

    private static final Map<Biome, BiomeColumnBlocks> BIOME_BLOCKS = Map.of(
            Biome.PLAINS, new BiomeColumnBlocks(Block.GRASS, Block.DIRT, Block.STONE),
            Biome.FOREST, new BiomeColumnBlocks(Block.GRASS, Block.DIRT, Block.STONE),
            Biome.MOUNTAINS, new BiomeColumnBlocks(Block.STONE, Block.STONE, Block.STONE),
            Biome.DESERT, new BiomeColumnBlocks(Block.SAND, Block.SANDSTONE, Block.STONE),
            Biome.OCEAN, new BiomeColumnBlocks(Block.SAND, Block.GRAVEL, Block.STONE));

    private void fillColumn(Chunk chunk, int localX, int localZ, int surfaceY, Biome biome) {
        BiomeColumnBlocks blocks = BIOME_BLOCKS.get(biome);
        int stoneTop = surfaceY - DIRT_DEPTH - 1;
        int waterStart = surfaceY + 1;

        chunk.setBlock(localX, 0, localZ, Block.BEDROCK);
        for (int y = 1; y <= stoneTop; y++) chunk.setBlock(localX, y, localZ, blocks.depth());
        chunk.setBlock(localX, surfaceY - DIRT_DEPTH, localZ, blocks.subsurface());
        chunk.setBlock(localX, surfaceY - 1, localZ, blocks.subsurface());
        chunk.setBlock(localX, surfaceY, localZ, chooseSurface(surfaceY, biome, blocks));
        for (int y = waterStart; y <= SEA_LEVEL; y++) chunk.setBlock(localX, y, localZ, Block.WATER);
    }

    // Mountains: always stone, snow above SNOW_LINE - height determines surface, not sea level.
    // Everything else: use biome surface above sea level; sand below (flooded terrain floor).
    private static Block chooseSurface(int surfaceY, Biome biome, BiomeColumnBlocks blocks) {
        if (biome == Biome.MOUNTAINS) {
            return surfaceY >= SNOW_LINE ? Block.SNOW : blocks.surface();
        }
        return surfaceY > SEA_LEVEL ? blocks.surface() : Block.SAND;
    }

    // Per-column seed derived by XOR-mixing world seed with world coords using large primes.
    // Changing X or Z by even 1 block produces a completely different colSeed, so ore
    // placement is uncorrelated between columns. Same formula is used in placeTrees so
    // the two passes share the same per-column identity without sharing a Random instance.
    private void placeOres(Chunk chunk, long seed, int worldX, int worldZ, int localX, int localZ) {
        long colSeed = seed ^ ((long) worldX * COL_SEED_PRIME_X) ^ ((long) worldZ * COL_SEED_PRIME_Z);
        Random colRng = new Random(colSeed);

        for (IGenerationSpec.OreSpec ore : oreSpecs) {
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
