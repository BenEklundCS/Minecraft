package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;

// writes
public interface IWorldMutator {
    void setBlock(int x, int y, int z, Block block);

    void markNeighborsDirty(ChunkPos pos);
}
