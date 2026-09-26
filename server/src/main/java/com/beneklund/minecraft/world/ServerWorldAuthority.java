package com.beneklund.minecraft.world;

import static com.beneklund.minecraft.util.Log.WORLD;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.chunk.ChunkState;
import com.beneklund.minecraft.world.chunk.ChunkWithNeighbors;
import com.beneklund.minecraft.world.chunk.LightEngine;
import java.util.List;

/**
 * The server's {@link IWorldAuthority}: reads and writes blocks directly in the authoritative
 * {@link World}.
 *
 * <p>Reads outside the world's height or in an unloaded chunk return air. Writes there are
 * ignored, so callers that need to know whether an edit landed must check first, as {@code
 * GameServer.applyEdit} does. A successful write marks the chunk {@link ChunkState#DIRTY} and, for
 * a border block or a removed light source, dirties the neighbours whose meshes or light it
 * affects.
 */
public class ServerWorldAuthority implements IWorldAuthority {
    private record ChunkCoordinates(int x, int z) {}

    private final World world;
    private final BlockRegistry registry;
    private final LightEngine lightEngine;

    public ServerWorldAuthority(World world, BlockRegistry registry, LightEngine lightEngine) {
        this.world = world;
        this.registry = registry;
        this.lightEngine = lightEngine;
    }

    @Override
    public BlockDef getBlock(int x, int y, int z) {
        if (!Chunk.inYRange(y)) return registry.get(Block.AIR);
        Chunk chunk = world.getChunk(ChunkPos.containing(x, z));
        if (chunk == null) return registry.get(Block.AIR);
        ChunkCoordinates chunkCoordinates = getChunkCoordinates(x, z);
        return registry.get(chunk.getBlock(chunkCoordinates.x, y, chunkCoordinates.z));
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        if (!Chunk.inYRange(y)) return;
        ChunkPos pos = ChunkPos.containing(x, z);
        Chunk chunk = world.getChunk(pos);
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
        if (atChunkBorder(chunkCoordinates)) {
            WORLD.trace("edit on chunk border, marking neighbours of {} dirty", pos);
            markNeighborsDirty(pos);
        }
    }

    /**
     * Moves every {@link ChunkState#UPLOADED} chunk among the 8 around {@code pos} to {@link
     * ChunkState#DIRTY}.
     *
     * <p>All 8, including diagonals, because a block in a chunk corner is one of the ambient
     * occlusion samples for the vertex the diagonal neighbour shares with it, so that neighbour's
     * mesh is stale too.
     *
     * <p>Returns immediately if the chunk at {@code pos} is gone, which lets this reuse {@link
     * ChunkWithNeighbors}, which refuses a null centre. A generation job can outlive its chunk, and
     * a chunk that's gone has no neighbours to mark.
     */
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

    private ChunkCoordinates getChunkCoordinates(int worldX, int worldZ) {
        // floorMod gives a non-negative remainder, matching the floorDiv in ChunkPos.containing.
        int chunkX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int chunkZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        return new ChunkCoordinates(chunkX, chunkZ);
    }

    private boolean atChunkBorder(ChunkCoordinates coords) {
        return (coords.x == 0 || coords.x == Chunk.SIZE_XZ - 1) || (coords.z == 0 || coords.z == Chunk.SIZE_XZ - 1);
    }
}
