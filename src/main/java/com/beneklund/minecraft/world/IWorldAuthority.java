package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import java.util.List;

public interface IWorldAuthority {
    BlockDef getBlock(int x, int y, int z);

    void setBlock(int x, int y, int z, Block block);

    Chunk getChunk(ChunkPos pos);

    List<Entity> getEntities(AABB aabb);

    void markNeighborsDirty(ChunkPos pos);
}
