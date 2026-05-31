package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.Chunk;

public class TreePlacer {
    private static final int TRUNK_HEIGHT = 5;
    private static final int LOWER_CANOPY_START = TRUNK_HEIGHT - 1;
    private static final int LOWER_CANOPY_END = TRUNK_HEIGHT;
    private static final int UPPER_CANOPY_START = TRUNK_HEIGHT + 1;
    private static final int UPPER_CANOPY_END = TRUNK_HEIGHT + 2;
    private static final int LOWER_CANOPY_RADIUS = 2;
    private static final int UPPER_CANOPY_RADIUS = 1;

    // Places a 5-block oak trunk topped with a two-tier leaf canopy.
    // Blocks outside chunk bounds are silently skipped — edge trees are truncated.
    public void placeTree(Chunk chunk, int localX, int surfaceY, int localZ) {
        for (int y = surfaceY + 1; y <= surfaceY + TRUNK_HEIGHT; y++) {
            if (inBounds(localX, y, localZ)) {
                chunk.setBlock(localX, y, localZ, Block.OAK_LOG);
            }
        }

        // lower canopy
        for (int dy = LOWER_CANOPY_START; dy <= LOWER_CANOPY_END; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, LOWER_CANOPY_RADIUS);
        }

        // upper canopy: tighter 3x3 ring above the trunk tip
        for (int dy = UPPER_CANOPY_START; dy <= UPPER_CANOPY_END; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, UPPER_CANOPY_RADIUS);
        }
    }

    // Fills a square ring of leaves at the given y, radius blocks out from (cx, cz).
    // Leaves never overwrite existing non-air blocks.
    private void placeLeavesRing(Chunk chunk, int cx, int y, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int bx = cx + dx, bz = cz + dz;
                if (!inBounds(bx, y, bz)) continue;
                if (chunk.getBlock(bx, y, bz) == Block.AIR) {
                    chunk.setBlock(bx, y, bz, Block.OAK_LEAF);
                }
            }
        }
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < Chunk.SIZE_XZ && z >= 0 && z < Chunk.SIZE_XZ && y >= 0 && y < Chunk.SIZE_Y;
    }
}
