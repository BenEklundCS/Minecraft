package com.beneklund.minecraft.world;

import static com.beneklund.minecraft.util.Log.WORLD;

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
    private final LightEngine lightEngine;

    public LocalWorldAuthority(World world, BlockRegistry registry, LightEngine lightEngine) {
        this.world = world;
        this.registry = registry;
        this.lightEngine = lightEngine;
    }

    @Override
    public BlockDef getBlock(int x, int y, int z) {
        if (!Chunk.inYRange(y)) return registry.get(Block.AIR);
        Chunk chunk = getChunk(x, z);
        if (chunk == null) return registry.get(Block.AIR);
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        return registry.get(chunk.getBlock(chunkCoordinates.x, y, chunkCoordinates.z));
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        if (!Chunk.inYRange(y)) return;
        Chunk chunk = getChunk(x, z);
        ChunkPos pos = getChunkPos(x, z);
        if (chunk == null) return;
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        BlockDef previous = registry.get(chunk.getBlock(chunkCoordinates.x, y, chunkCoordinates.z));
        chunk.setBlock(chunkCoordinates.x, y, chunkCoordinates.z, block);
        chunk.tryTransition(ChunkState.DIRTY);
        // An emitter throws light up to 15 blocks, so by the time it's broken its glow is baked into
        // the surrounding chunks' LightMaps and meshes. Those chunks have no edit of their own to
        // remesh for, and a from-scratch recompute of this one would only read the glow back off
        // them, so the light has to be taken out by hand and every chunk that held it re-meshed.
        if (previous.lightLevel() > 0) {
            lightEngine.removeBlockLight(this, x, y, z, previous.lightLevel());
            markNeighborsDirty(pos);
        }
        // This is the player-edit path only — world generation writes straight into the Chunk — so
        // one line per edit is the right granularity, not thousands per generated chunk.
        WORLD.debug("setBlock {} at world ({}, {}, {}) in chunk {}", block, x, y, z, pos);
        // edit occured on border, mark neighbors dirty if they're uploaded
        if (atChunkBorder(chunkCoordinates)) {
            WORLD.trace("edit on chunk border, marking neighbours of {} dirty", pos);
            markNeighborsDirty(pos);
        }
    }

    // All 8 surrounding chunks, not just the 4 cardinals: a block in a chunk corner is one of the
    // AO samples for the vertex the diagonal neighbor shares with it, so that neighbor's mesh is
    // stale too.
    //
    // The early return is what lets this reuse ChunkWithNeighbors, which refuses a null center.
    // A generation job can outlive its chunk — tick() may have unloaded it by the time this runs
    // — and there are no neighbors to mark for a chunk that's gone anyway.
    public void markNeighborsDirty(ChunkPos pos) {
        if (getChunk(pos) == null) return;
        for (Chunk neighbor : ChunkWithNeighbors.around(pos, this::getChunk).neighbors()) {
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

    private boolean atChunkBorder(ChunkCoordinates coords) {
        return (coords.x == 0 || coords.x == Chunk.SIZE_XZ - 1) || (coords.z == 0 || coords.z == Chunk.SIZE_XZ - 1);
    }
}
