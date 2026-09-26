package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;

/** The write half of {@link IWorldAuthority}, in world block coordinates. */
public interface IWorldMutator {
    void setBlock(int x, int y, int z, Block block);

    /** Schedules a remesh of the chunks around {@code pos} whose meshes an edit there made stale. */
    void markNeighborsDirty(ChunkPos pos);
}
