package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import java.util.List;

public interface IWorldAuthority {
    BlockDef getBlock(int x, int y, int z);

    void setBlock(int x, int y, int z, byte id);

    Chunk getChunk(ChunkPos pos);

    List<Entity> getEntities(AABB aabb);

    void markCardinalNeighborsDirty(ChunkPos pos);
}
