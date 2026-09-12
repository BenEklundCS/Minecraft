package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.IServerLink;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.World;
import java.util.List;

// Reads the replica; writes go to the server and show up when BlockChanged comes back.
public class ClientWorldAuthority implements IWorldAuthority {
    private final World world;
    private final BlockRegistry registry;
    private final IServerLink serverLink;

    public ClientWorldAuthority(World world, BlockRegistry registry, IServerLink serverLink) {
        this.world = world;
        this.registry = registry;
        this.serverLink = serverLink;
    }

    @Override
    public BlockDef getBlock(int x, int y, int z) {
        if (!Chunk.inYRange(y)) return registry.get(Block.AIR);
        Chunk chunk = world.getChunk(ChunkPos.containing(x, z));
        if (chunk == null) return registry.get(Block.AIR);
        return registry.get(chunk.getBlock(Math.floorMod(x, Chunk.SIZE_XZ), y, Math.floorMod(z, Chunk.SIZE_XZ)));
    }

    // tick 0: GameServer doesn't read it yet.
    @Override
    public void setBlock(int x, int y, int z, Block block) {
        serverLink.send(new IPacket.ToServer.BlockEdit(0, x, y, z, block, block == Block.AIR));
    }

    // ClientChunkManager remeshes when the change arrives.
    @Override
    public void markNeighborsDirty(ChunkPos pos) {}

    @Override
    public Chunk getChunk(ChunkPos pos) {
        return world.getChunk(pos);
    }

    @Override
    public List<Entity> getEntities(AABB aabb) {
        return List.of();
    }
}
