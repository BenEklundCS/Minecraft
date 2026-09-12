package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import java.util.List;

// reads
public interface IWorldView {
    BlockDef getBlock(int x, int y, int z);

    Chunk getChunk(ChunkPos pos);

    List<Entity> getEntities(AABB aabb);
}
