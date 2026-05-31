package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.Random;

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

    // Pure factory — same (pos, seed) always produces the same chunk.
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

    // Blends three noise layers at different scales to get a raw height value, then
    // selects a biome from a fourth very-low-frequency sample and applies that biome's
    // baseHeight + amplitude to produce the final surface Y for the column.
    private int computeSurfaceY(long seed, int worldX, int worldZ) {
        double continental = noiseHelper.noise2(seed, worldX, worldZ, 4, 0.5, 0.002);
        double erosion = noiseHelper.noise2(seed + 100, worldX, worldZ, 3, 0.5, 0.008);
        double detail = noiseHelper.noise2(seed + 200, worldX, worldZ, 2, 0.5, 0.04);
        double raw = continental * 0.5 + erosion * 0.3 + detail * 0.2;

        // seed offset prevents biome pattern from correlating with terrain pattern
        double biomeNoise = noiseHelper.noise2(seed + 300, worldX, worldZ, 1, 0.5, 0.0005);
        Biome[] biomes = Biome.values();
        int biomeIndex = Math.clamp((int) ((biomeNoise + 1.0) / 2.0 * biomes.length), 0, biomes.length - 1);
        Biome biome = biomes[biomeIndex];

        return Math.clamp((int) (biome.getBaseHeight() + raw * biome.getAmplitude()), 4, 250);
    }

    // Fills one column: bedrock at y=0, stone up to surfaceY-3, two dirt layers,
    // then grass or sand at the surface. Submerged columns get water filled up to SEA_LEVEL.
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

    // Scatters coal and iron ore inside the stone layer using a deterministic per-column
    // RNG derived from the world seed, so ore positions are stable across regeneration.
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

    // Places trees on ~5% of grass columns that are above sea level and have enough
    // vertical headroom. Uses the same per-column seed formula as ore placement so
    // tree positions are deterministic.
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

    // Carves caves by treating 3D noise above a threshold as air. The y > 4 guard
    // keeps the bedrock floor intact.
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
