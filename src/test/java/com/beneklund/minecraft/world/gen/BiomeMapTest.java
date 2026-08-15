package com.beneklund.minecraft.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Measurements of the biome map's shape, sampled without generating chunks.
//
// The histogram says how much of the world each biome gets; the edge count says how much
// boundary those regions have. A circle is the least perimeter a given area can have, so for
// a fixed histogram a rising edge count is the literal statement that the shapes got less
// round. Almost nothing here asserts a share is correct — locking in current shares would
// produce a test that fails on every improvement to the map.
class BiomeMapTest {

    private static final int CENSUS_EXTENT = 4096;
    private static final int CENSUS_STEP = 16;
    private static final int CENSUS_SIDE = CENSUS_EXTENT / CENSUS_STEP;
    private static final int CENSUS_SAMPLES = CENSUS_SIDE * CENSUS_SIDE;

    // Measured at seed 42 over this grid, for reference. Shares are printed, not asserted:
    // locking them in produces a test that fails on every improvement to the map.
    //
    //   unwarped, 1 octave   PLAINS 23.32%  FOREST 36.31%  MOUNTAINS 31.14%
    //                        DESERT 4.98%  OCEAN 4.25%   | edges 2989
    //   warped,   1 octave   PLAINS 35.14%  FOREST 29.82%  MOUNTAINS 22.81%
    //                        DESERT 5.53%  OCEAN 6.70%   | edges 3932
    //
    // The warp moves shares by up to 58%, so it is not a boundary-only change: at strength 700
    // against a 2860-block warp field the coordinate map is far from the identity, and it
    // compresses some regions while stretching others. Edge count is no use as a check either —
    // warping both axes from one seed offset measures 3966 edges against this 3932, scoring
    // higher than the implementation it is supposed to fail.

    // Pearson correlation between the two warp displacements. Sharing a seed offset makes them
    // the same number at every point, so r is exactly 1; independent fields sit near 0.
    private static final double MAX_WARP_AXIS_CORRELATION = 0.5;

    // Ores, trees and caves all overwrite or expose blocks after fillColumn has run, so with
    // the full spec list the top block of a column is not reliably the one the biome chose.
    // Filtering the defaults rather than retyping them means later edits to the two BiomeSpecs
    // are picked up here automatically — a hand-copied spec list would silently census the old
    // map. Stream order is preserved, so the temperature spec stays first.
    private static final List<IGenerationSpec> TERRAIN_ONLY = IGenerationSpec.DEFAULT_WORLD_GENERATION.stream()
            .filter(s -> s instanceof IGenerationSpec.NoiseLayersSpec || s instanceof IGenerationSpec.BiomeSpec)
            .toList();

    // Exactly what chooseSurface can return per biome: MOUNTAINS switches on the snow line,
    // everything else swaps its surface block for SAND at or below sea level. DESERT and OCEAN
    // both land on SAND either way, so this cannot tell those two apart — the point is to catch
    // biomeAt and generate reading different climate, not to identify a biome from a block.
    private static final Map<Biome, Set<Block>> SURFACE_BLOCKS = Map.of(
            Biome.PLAINS, Set.of(Block.GRASS, Block.SAND),
            Biome.FOREST, Set.of(Block.GRASS, Block.SAND),
            Biome.MOUNTAINS, Set.of(Block.STONE, Block.SNOW),
            Biome.DESERT, Set.of(Block.SAND),
            Biome.OCEAN, Set.of(Block.SAND));

    private final long seed = 42L;
    private final WorldGenerator generator = new WorldGenerator(BlockRegistry.createDefault());

    @Test
    void biomeAt_sameCoordinateTwice_sameBiome() {
        for (int[] c : new int[][] {{0, 0}, {1234, -5678}, {-99_999, 4242}}) {
            Biome first = generator.biomeAt(seed, c[0], c[1]);
            assertEquals(
                    first, generator.biomeAt(seed, c[0], c[1]), "biomeAt is not stable at (" + c[0] + "," + c[1] + ")");
        }
    }

    // The reason biomeAt exists is that it reaches the same climate sample generate does.
    // Four chunks spread across the census square rather than one, because a single chunk is
    // 256 columns of very likely the same biome — which is the map problem this all exists for.
    @Test
    void biomeAt_agreesWithTheBiomeGenerateUsed() {
        WorldGenerator terrainOnly = new WorldGenerator(BlockRegistry.createDefault(), TERRAIN_ONLY);
        List<ChunkPos> positions =
                List.of(new ChunkPos(0, 0), new ChunkPos(64, 64), new ChunkPos(-64, 96), new ChunkPos(128, -32));

        for (ChunkPos pos : positions) {
            Chunk chunk = new Chunk();
            terrainOnly.generate(pos, seed, chunk);

            for (int localX = 0; localX < Chunk.SIZE_XZ; localX++) {
                for (int localZ = 0; localZ < Chunk.SIZE_XZ; localZ++) {
                    int worldX = pos.x() * Chunk.SIZE_XZ + localX;
                    int worldZ = pos.z() * Chunk.SIZE_XZ + localZ;

                    Biome biome = terrainOnly.biomeAt(seed, worldX, worldZ);
                    Block top = topSolid(chunk, localX, localZ);
                    assertTrue(
                            SURFACE_BLOCKS.get(biome).contains(top),
                            "at (" + worldX + "," + worldZ + ") biomeAt said " + biome + " but the surface block is "
                                    + top);
                }
            }
        }
    }

    @Test
    void biomeCensus_printsTheCurrentHistogramAndEdgeCount() {
        Biome[][] map = censusMap();
        Map<Biome, Integer> counts = histogram(map);
        System.out.println(report(counts, edgeCount(map)));

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(CENSUS_SAMPLES, total, "every sample must land in exactly one biome");
        assertTrue(counts.get(Biome.PLAINS) > 0, "PLAINS absent from the census");
    }

    // What separates a domain warp from a shear is that the two axes move independently. Sharing
    // one seed offset displaces every point by (d, d) — the map slides along the diagonal and the
    // biome shapes are not bent, only skewed. Neither the histogram nor the edge count catches
    // that (the shared-seed version scores more edges, not fewer), but the displacement field
    // does: correlation 1.0 when shared, near 0 when the offsets differ.
    //
    // Recomputed here rather than read back from the map because the wrong implementation
    // produces a perfectly plausible map. The defect is only visible in how it got there.
    @Test
    void climateWarp_displacesTheTwoAxesIndependently() {
        int half = CENSUS_EXTENT / 2;
        double sumX = 0;
        double sumZ = 0;
        double sumXX = 0;
        double sumZZ = 0;
        double sumXZ = 0;

        for (int i = 0; i < CENSUS_SIDE; i++) {
            for (int j = 0; j < CENSUS_SIDE; j++) {
                int x = -half + i * CENSUS_STEP;
                int z = -half + j * CENSUS_STEP;
                double dx = generator.warpX(
                                seed,
                                WorldGenerator.BIOME_WARP_SEED_X,
                                x,
                                z,
                                WorldGenerator.BIOME_WARP_STRENGTH,
                                WorldGenerator.BIOME_WARP_SCALE)
                        - x;
                double dz = generator.warpZ(
                                seed,
                                WorldGenerator.BIOME_WARP_SEED_Z,
                                x,
                                z,
                                WorldGenerator.BIOME_WARP_STRENGTH,
                                WorldGenerator.BIOME_WARP_SCALE)
                        - z;
                sumX += dx;
                sumZ += dz;
                sumXX += dx * dx;
                sumZZ += dz * dz;
                sumXZ += dx * dz;
            }
        }

        double n = CENSUS_SAMPLES;
        double covariance = sumXZ / n - (sumX / n) * (sumZ / n);
        double spreadX = Math.sqrt(sumXX / n - (sumX / n) * (sumX / n));
        double spreadZ = Math.sqrt(sumZZ / n - (sumZ / n) * (sumZ / n));
        double r = covariance / (spreadX * spreadZ);

        assertTrue(
                Math.abs(r) < MAX_WARP_AXIS_CORRELATION,
                String.format(
                        "the two warp axes correlate at r=%.3f — at 1.0 they are one field and the map is sheared "
                                + "along the diagonal rather than warped. Check BIOME_WARP_SEED_X and "
                                + "BIOME_WARP_SEED_Z are different.",
                        r));
    }

    // The warp must not lose a biome entirely. Shares themselves are printed, not asserted.
    @Test
    void climateWarp_keepsEveryBiomeOnTheMap() {
        Biome[][] map = censusMap();
        Map<Biome, Integer> counts = histogram(map);
        for (Biome b : Biome.values()) {
            assertTrue(counts.get(b) > 0, b + " vanished from the census. " + report(counts, edgeCount(map)));
        }
    }

    private Biome[][] censusMap() {
        Biome[][] map = new Biome[CENSUS_SIDE][CENSUS_SIDE];
        int half = CENSUS_EXTENT / 2;
        for (int i = 0; i < CENSUS_SIDE; i++) {
            for (int j = 0; j < CENSUS_SIDE; j++) {
                map[i][j] = generator.biomeAt(seed, -half + i * CENSUS_STEP, -half + j * CENSUS_STEP);
            }
        }
        return map;
    }

    private static Map<Biome, Integer> histogram(Biome[][] map) {
        EnumMap<Biome, Integer> counts = new EnumMap<>(Biome.class);
        for (Biome b : Biome.values()) {
            counts.put(b, 0);
        }
        for (Biome[] row : map) {
            for (Biome b : row) {
                counts.merge(b, 1, Integer::sum);
            }
        }
        return counts;
    }

    // Adjacent sample pairs whose biome differs, counted once per pair. For a fixed set of
    // regions this is proportional to total boundary length.
    private static int edgeCount(Biome[][] map) {
        int edges = 0;
        for (int i = 0; i < CENSUS_SIDE; i++) {
            for (int j = 0; j < CENSUS_SIDE; j++) {
                if (i + 1 < CENSUS_SIDE && map[i][j] != map[i + 1][j]) edges++;
                if (j + 1 < CENSUS_SIDE && map[i][j] != map[i][j + 1]) edges++;
            }
        }
        return edges;
    }

    private static String report(Map<Biome, Integer> counts, int edges) {
        StringBuilder sb = new StringBuilder("biome census: ");
        for (Map.Entry<Biome, Integer> e : counts.entrySet()) {
            sb.append(
                    String.format("%s %d (%.2f%%)  ", e.getKey(), e.getValue(), 100.0 * e.getValue() / CENSUS_SAMPLES));
        }
        return sb.append("| edges ").append(edges).toString();
    }

    // Topmost block that isn't air or water. With ores, trees and caves switched off that is
    // exactly the block fillColumn chose for the surface.
    private static Block topSolid(Chunk chunk, int localX, int localZ) {
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            Block b = chunk.getBlock(localX, y, localZ);
            if (b != Block.AIR && b != Block.WATER) return b;
        }
        return Block.AIR;
    }
}
