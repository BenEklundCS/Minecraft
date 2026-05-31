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

    private final BlockRegistry registry;
    private final NoiseHelper noiseHelper;
    private final TreePlacer treePlacer;
    private final List<GenerationSpec.OreSpec> oreSpecs;
    private final GenerationSpec.TreeSpec treeSpec;
    private final GenerationSpec.CaveSpec caveSpec;
    private final GenerationSpec.NoiseLayersSpec noiseLayers;

    // Convenience constructor for tests — uses vanilla-approximate defaults.
    public WorldGenerator(BlockRegistry registry) {
        this(
                registry,
                List.of(
                        new GenerationSpec.NoiseLayersSpec(
                                new GenerationSpec.NoiseLayerSpec(4, 0.002, 0.5, 0.5, 0),
                                new GenerationSpec.NoiseLayerSpec(3, 0.008, 0.5, 0.3, 100),
                                new GenerationSpec.NoiseLayerSpec(2, 0.04, 0.5, 0.2, 200)),
                        new GenerationSpec.OreSpec(Block.COAL_ORE, 5, 50, 0.01f),
                        new GenerationSpec.OreSpec(Block.IRON_ORE, 5, 30, 0.005f),
                        new GenerationSpec.TreeSpec(0.05f, 8),
                        new GenerationSpec.CaveSpec(0.6, 2, 0.04, 0.5, 5)));
    }

    public WorldGenerator(BlockRegistry registry, List<GenerationSpec> specs) {
        this.registry = registry;
        this.noiseHelper = new NoiseHelper();
        this.treePlacer = new TreePlacer();

        List<GenerationSpec.OreSpec> ores = new ArrayList<>();
        GenerationSpec.NoiseLayersSpec layers = null;
        GenerationSpec.TreeSpec tree = null;
        GenerationSpec.CaveSpec cave = null;
        for (GenerationSpec spec : specs) {
            if (spec instanceof GenerationSpec.OreSpec ore) ores.add(ore);
            else if (spec instanceof GenerationSpec.NoiseLayersSpec n) layers = n;
            else if (spec instanceof GenerationSpec.TreeSpec t) tree = t;
            else if (spec instanceof GenerationSpec.CaveSpec c) cave = c;
        }
        this.oreSpecs = ores;
        this.noiseLayers = layers;
        this.treeSpec = tree;
        this.caveSpec = cave;
    }

    @Override
    public Chunk generate(ChunkPos pos, long seed) {
        Chunk chunk = new Chunk(new byte[Chunk.SIZE_XZ * Chunk.SIZE_XZ * Chunk.SIZE_Y]);
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

        return chunk;
    }

    // Biome noise is separate from the terrain layers — it drives biome selection, not blending.
    private int computeSurfaceY(long seed, int worldX, int worldZ) {
        double raw = sampleLayer(seed, worldX, worldZ, noiseLayers.continental())
                + sampleLayer(seed, worldX, worldZ, noiseLayers.erosion())
                + sampleLayer(seed, worldX, worldZ, noiseLayers.detail());

        double biomeNoise = noiseHelper.noise2(seed + 300, worldX, worldZ, 1, 0.5, 0.0005);
        Biome[] biomes = Biome.values();
        int biomeIndex = Math.clamp((int) ((biomeNoise + 1.0) / 2.0 * biomes.length), 0, biomes.length - 1);
        Biome biome = biomes[biomeIndex];

        return Math.clamp((int) (biome.getBaseHeight() + raw * biome.getAmplitude()), 4, 250);
    }

    private double sampleLayer(long seed, int x, int z, GenerationSpec.NoiseLayerSpec layer) {
        return noiseHelper.noise2(seed + layer.seedOffset(), x, z, layer.octaves(), layer.persistence(), layer.scale())
                * layer.weight();
    }

    // Column stack from bottom to top:
    //   y=0             BEDROCK  (never carve-able; carveCaves guards y > 4 anyway)
    //   y=1..surfY-3    STONE    (ore placement targets this range)
    //   y=surfY-2,-1    DIRT     (two-layer dirt cap, mimics vanilla)
    //   y=surfY         GRASS or SAND (sand when surface is at or below sea level)
    //   y=surfY+1..62   WATER    (only when column is submerged)
    private void fillColumn(Chunk chunk, int localX, int localZ, int surfaceY) {
        chunk.setBlock(localX, 0, localZ, Block.BEDROCK);

        for (int y = 1; y <= surfaceY - 3; y++) {
            chunk.setBlock(localX, y, localZ, Block.STONE);
        }

        chunk.setBlock(localX, surfaceY - 2, localZ, Block.DIRT);
        chunk.setBlock(localX, surfaceY - 1, localZ, Block.DIRT);

        byte surface = surfaceY > SEA_LEVEL ? Block.GRASS : Block.SAND;
        chunk.setBlock(localX, surfaceY, localZ, surface);

        for (int y = surfaceY + 1; y <= SEA_LEVEL; y++) {
            chunk.setBlock(localX, y, localZ, Block.WATER);
        }
    }

    // Per-column seed derived by XOR-mixing world seed with world coords using large primes.
    // Changing X or Z by even 1 block produces a completely different colSeed, so ore
    // placement is uncorrelated between columns. Same formula is used in placeTrees so
    // the two passes share the same per-column identity without sharing a Random instance.
    private void placeOres(Chunk chunk, long seed, int worldX, int worldZ, int localX, int localZ) {
        long colSeed = seed ^ ((long) worldX * 341873128712L) ^ ((long) worldZ * 132897987541L);
        Random colRng = new Random(colSeed);

        for (GenerationSpec.OreSpec ore : oreSpecs) {
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
                long colSeed = seed ^ ((long) worldX * 341873128712L) ^ ((long) worldZ * 132897987541L);
                if (new Random(colSeed).nextFloat() >= treeSpec.spawnChance()) continue;

                treePlacer.placeTree(chunk, localX, surfaceY, localZ);
            }
        }
    }

    // seed+400 keeps cave noise independent of terrain layers (offsets 0–300).
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
                            seed + 400,
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
