package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.Chunk;

public class TreePlacer {

    // Places a 5-block oak trunk topped with a two-tier leaf canopy.
    // Blocks outside chunk bounds are silently skipped — edge trees are truncated.
    public void placeTree(Chunk chunk, int localX, int surfaceY, int localZ) {
        for (int y = surfaceY + 1; y <= surfaceY + 5; y++) {
            if (inBounds(localX, y, localZ)) {
                chunk.setBlock(localX, y, localZ, Block.OAK_LOG);
            }
        }

        // lower canopy: full 5x5 ring around the top two trunk layers
        for (int dy = 4; dy <= 5; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, 2);
        }

        // upper canopy: tighter 3x3 ring above the trunk tip
        for (int dy = 6; dy <= 7; dy++) {
            placeLeavesRing(chunk, localX, surfaceY + dy, localZ, 1);
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
