package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public void setBlock(int x, int y, int z, Block block) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        Chunk chunk = getChunk(x, z);
        ChunkPos pos = getChunkPos(x, z);
        if (chunk == null) return;
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        chunk.setBlock(chunkCoordinates.x, y, chunkCoordinates.z, block);
        chunk.tryTransition(ChunkState.DIRTY);
        // edit occured on border, mark neighbors dirty if they're uploaded
        if (atChunkBorder(chunkCoordinates)) {
            markNeighborsDirty(pos);
        }
    }

    // All 8 surrounding chunks, not just the 4 cardinals: a block in a chunk corner is one of the
    // AO samples for the vertex the diagonal neighbor shares with it, so that neighbor's mesh is
    // stale too.
    public void markNeighborsDirty(ChunkPos pos) {
        for (Chunk neighbor : getNeighbors(pos)) {
            if (neighbor.getState() == ChunkState.UPLOADED) {
                neighbor.tryTransition(ChunkState.DIRTY);
            }
        }
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
        ChunkPos pos = getChunkPos(x, z);
        return world.getChunk(pos);
    }

    private ChunkPos getChunkPos(int x, int z) {
        // floorDiv, not /, so negative world coords map to the right chunk (e.g. x=-1 → chunk -1, not 0).
        return new ChunkPos(Math.floorDiv(x, Chunk.SIZE_XZ), Math.floorDiv(z, Chunk.SIZE_XZ));
    }

    private ChunkCoordinates getChunkCoordinates(int worldX, int worldZ) {
        // floorMod gives a non-negative remainder, matching floorDiv above.
        int chunkX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int chunkZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        return new ChunkCoordinates(chunkX, chunkZ);
    }

    // Takes chunk coordinates, so it goes through the ChunkPos overload of getChunk. The int
    // overload divides by SIZE_XZ because it expects world coordinates — handing it pos.x() + 1
    // resolves to whatever chunk contains world column 1, not the neighbor.
    private List<Chunk> getNeighbors(ChunkPos pos) {
        List<Chunk> neighbors = new ArrayList<>();
        neighbors.add(getChunk(new ChunkPos(pos.x(), pos.z() - 1))); // NORTH
        neighbors.add(getChunk(new ChunkPos(pos.x(), pos.z() + 1))); // SOUTH
        neighbors.add(getChunk(new ChunkPos(pos.x() + 1, pos.z()))); // EAST
        neighbors.add(getChunk(new ChunkPos(pos.x() - 1, pos.z()))); // WEST
        neighbors.add(getChunk(new ChunkPos(pos.x() + 1, pos.z() - 1))); // NORTH EAST
        neighbors.add(getChunk(new ChunkPos(pos.x() - 1, pos.z() - 1))); // NORTH WEST
        neighbors.add(getChunk(new ChunkPos(pos.x() + 1, pos.z() + 1))); // SOUTH EAST
        neighbors.add(getChunk(new ChunkPos(pos.x() - 1, pos.z() + 1))); // SOUTH WEST
        neighbors.removeIf(Objects::isNull);
        return neighbors;
    }

    private boolean atChunkBorder(ChunkCoordinates coords) {
        return (coords.x == 0 || coords.x == Chunk.SIZE_XZ - 1) || (coords.z == 0 || coords.z == Chunk.SIZE_XZ - 1);
    }
}
