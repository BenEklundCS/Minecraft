package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.List;

/** The read half of {@link IWorldAuthority}, in world block coordinates. */
public interface IWorldView {
    /** The block definition at a world position; air outside the world or in an unloaded chunk. */
    BlockDef getBlock(int x, int y, int z);

    /** The loaded chunk at {@code pos}, or {@code null}. */
    Chunk getChunk(ChunkPos pos);

    List<Entity> getEntities(AABB aabb);
}
