package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.chunk.Chunk;

/** Places oak trees: a five-block trunk under a two-tier square canopy. */
public class TreePlacer {
    private static final int TRUNK_HEIGHT = 5;
    private static final int LOWER_CANOPY_START = TRUNK_HEIGHT - 1;
    private static final int LOWER_CANOPY_END = TRUNK_HEIGHT;
    private static final int UPPER_CANOPY_START = TRUNK_HEIGHT + 1;
    private static final int UPPER_CANOPY_END = TRUNK_HEIGHT + 2;
    private static final int LOWER_CANOPY_RADIUS = 2;
    private static final int UPPER_CANOPY_RADIUS = 1;

    /**
     * Places one tree whose trunk starts above {@code surfaceY}. Blocks outside the chunk are
     * skipped, so a tree near a chunk edge loses the part of its canopy that falls in the
     * neighbour.
     */
    public void placeTree(Chunk chunk, int localX, int surfaceY, int localZ) {
        for (int y = surfaceY + 1; y <= surfaceY + TRUNK_HEIGHT; y++) {
            if (Chunk.inBounds(localX, y, localZ)) {
                chunk.setBlock(localX, y, localZ, Block.OAK_LOG);
            }
        }

        for (int dy = LOWER_CANOPY_START; dy <= LOWER_CANOPY_END; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, LOWER_CANOPY_RADIUS);
        }

        for (int dy = UPPER_CANOPY_START; dy <= UPPER_CANOPY_END; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, UPPER_CANOPY_RADIUS);
        }
    }

    /** Fills the square of half-width {@code radius} around (cx, cz) at {@code y} with leaves, in air only. */
    private void placeLeavesRing(Chunk chunk, int cx, int y, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int bx = cx + dx, bz = cz + dz;
                if (!Chunk.inBounds(bx, y, bz)) continue;
                if (chunk.getBlock(bx, y, bz) == Block.AIR) {
                    chunk.setBlock(bx, y, bz, Block.OAK_LEAF);
                }
            }
        }
    }
}
