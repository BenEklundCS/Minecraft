package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import java.util.List;

public class LocalWorldAuthority implements IWorldAuthority {
    private record ChunkCoordinates(int x, int z) {}

    private final World world;
    private final BlockRegistry registry;

    public LocalWorldAuthority(World world, BlockRegistry registry) {
        this.world = world;
        this.registry = registry;
    }

    @Override
    public BlockDef getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return registry.get(Block.AIR);
        Chunk chunk = getChunk(x, z);
        if (chunk == null) return registry.get(Block.AIR);
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        return registry.get(chunk.getBlock(chunkCoordinates.x, y, chunkCoordinates.z));
    }

    @Override
    public void setBlock(int x, int y, int z, byte id) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        Chunk chunk = getChunk(x, z);
        if (chunk == null) return;
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        chunk.setBlock(chunkCoordinates.x, y, chunkCoordinates.z, id);
        chunk.tryTransition(ChunkState.DIRTY);
        // TODO: an edit on a chunk border (local x/z == 0 or 15) leaves the neighbor chunk's
        // meshed faces stale - also markDirty the adjacent chunk(s) once meshing is wired up.
    }

    @Override
    public Chunk getChunk(ChunkPos pos) {
        return world.getChunk(pos);
    }

    @Override
    public List<Entity> getEntities(AABB aabb) {
        return List.of(); // stub — no entity tracking yet
    }

    private Chunk getChunk(int x, int z) {
        // floorDiv, not /, so negative world coords map to the right chunk (e.g. x=-1 → chunk -1, not 0).
        ChunkPos pos = new ChunkPos(Math.floorDiv(x, Chunk.SIZE_XZ), Math.floorDiv(z, Chunk.SIZE_XZ));
        return this.world.getChunk(pos);
    }

    private ChunkCoordinates getChunkCoordinates(int worldX, int worldZ) {
        // floorMod gives a non-negative remainder, matching floorDiv above.
        int chunkX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int chunkZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        return new ChunkCoordinates(chunkX, chunkZ);
    }
}
