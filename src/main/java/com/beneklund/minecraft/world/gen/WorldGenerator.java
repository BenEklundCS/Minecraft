package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
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

    public WorldGenerator(BlockRegistry registry) {
        this.registry = registry;
        this.noiseHelper = new NoiseHelper();
        this.treePlacer = new TreePlacer();
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

    // Three noise layers blend to form the raw [-1,1] height value, then a biome
    // stretches and shifts it into a final Y coordinate.
    //
    // Layer weights and roles:
    //   continental  scale=0.002, 4oct, 50% weight — slow large-scale shapes (continent vs ocean basin)
    //   erosion      scale=0.008, 3oct, 30% weight — medium variation, smoothed ridges
    //   detail       scale=0.04,  2oct, 20% weight — fine surface bumps
    //
    // Each layer uses a different seed offset (+0, +100, +200) so the three noise fields
    // are statistically independent — changing one layer's parameters doesn't shift the others.
    //
    // Biome is picked from a fourth sample at scale=0.0005 (seed+300 keeps it independent),
    // which changes so slowly that biome transitions are hundreds of blocks wide.
    // The biome maps the -1..1 noise range onto its own baseHeight ± amplitude band.
    private int computeSurfaceY(long seed, int worldX, int worldZ) {
        double continental = noiseHelper.noise2(seed, worldX, worldZ, 4, 0.5, 0.002);
        double erosion = noiseHelper.noise2(seed + 100, worldX, worldZ, 3, 0.5, 0.008);
        double detail = noiseHelper.noise2(seed + 200, worldX, worldZ, 2, 0.5, 0.04);
        double raw = continental * 0.5 + erosion * 0.3 + detail * 0.2;

        double biomeNoise = noiseHelper.noise2(seed + 300, worldX, worldZ, 1, 0.5, 0.0005);
        Biome[] biomes = Biome.values();
        int biomeIndex = Math.clamp((int) ((biomeNoise + 1.0) / 2.0 * biomes.length), 0, biomes.length - 1);
        Biome biome = biomes[biomeIndex];

        return Math.clamp((int) (biome.getBaseHeight() + raw * biome.getAmplitude()), 4, 250);
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

        for (int y = 5; y <= 50; y++) {
            if (chunk.getBlock(localX, y, localZ) == Block.STONE && colRng.nextFloat() < 0.01f) {
                chunk.setBlock(localX, y, localZ, Block.COAL_ORE);
            }
        }

        for (int y = 5; y <= 30; y++) {
            if (chunk.getBlock(localX, y, localZ) == Block.STONE && colRng.nextFloat() < 0.005f) {
                chunk.setBlock(localX, y, localZ, Block.IRON_ORE);
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
                if (surfaceY + 8 >= Chunk.SIZE_Y) continue;

                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;
                long colSeed = seed ^ ((long) worldX * 341873128712L) ^ ((long) worldZ * 132897987541L);
                if (new Random(colSeed).nextFloat() >= 0.05f) continue;

                treePlacer.placeTree(chunk, localX, surfaceY, localZ);
            }
        }
    }

    // Threshold > 0.6 means only the top ~20% of noise values carve air — caves are
    // intentionally sparse at this phase. Lower the threshold to get denser cave systems.
    // y > 4 guard preserves bedrock; seed+400 keeps cave noise independent of terrain.
    private void carveCaves(Chunk chunk, long seed, ChunkPos pos) {
        for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;

                for (int y = 5; y < Chunk.SIZE_Y; y++) {
                    if (chunk.getBlock(localX, y, localZ) == Block.BEDROCK) continue;
                    double caveNoise = noiseHelper.noise3(seed + 400, worldX, y, worldZ, 2, 0.5, 0.04);
                    if (caveNoise > 0.6) {
                        chunk.setBlock(localX, y, localZ, Block.AIR);
                    }
                }
            }
        }
    }
}
