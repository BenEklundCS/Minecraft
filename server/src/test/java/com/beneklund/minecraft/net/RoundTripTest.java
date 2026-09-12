package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.LightEngine;
import com.beneklund.minecraft.world.LocalWorldAuthority;
import com.beneklund.minecraft.world.World;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RoundTripTest {
    private static final int PLAYER_ID = 1;
    private static final int PROTOCOL_VERSION = 1;
    private static final long SEED = 42L;

    /*
     * One chunk at the world origin, holding one stone block. ChunkPos(0,0) is chosen so that
     * chunk-local and world coordinates coincide for x and z in 0..15 — Chunk.setBlock takes
     * LOCAL coordinates and IWorldView.getBlock takes WORLD ones, and at any other chunk these
     * constants would have to differ.
     */
    private static final ChunkPos ONLY_CHUNK = new ChunkPos(0, 0);
    private static final int EDIT_X = 0;
    private static final int EDIT_Y = 64;
    private static final int EDIT_Z = 0;

    // The world is kept alongside the server because BlockDef carries no back-reference to its
    // Block — see the note under this block — so the exact assertion has to read the Chunk.
    private record Fixture(GameServer server, World world) {}

    private static Fixture serverWithOneChunk() {
        World world = new World(new ConcurrentHashMap<>());
        Chunk chunk = new Chunk();
        chunk.setBlock(EDIT_X, EDIT_Y, EDIT_Z, Block.STONE);
        world.addChunk(ONLY_CHUNK, chunk);

        BlockRegistry registry = BlockRegistry.createDefault();
        LocalWorldAuthority authority = new LocalWorldAuthority(world, registry, new LightEngine(registry));
        return new Fixture(new GameServer(world, authority, SEED), world);
    }

    private static Block blockInWorld(World world) {
        return world.getChunk(ONLY_CHUNK).getBlock(EDIT_X, EDIT_Y, EDIT_Z);
    }

    @Test
    void joinIsAcceptedAndTheChunkIsStreamed() {
        Fixture fixture = serverWithOneChunk();
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        fixture.server().accept(pair.client());

        pair.server().send(new IPacket.ToServer.Join.Request("ben", PROTOCOL_VERSION));
        fixture.server().tick();

        List<IPacket.ToClient> received = pair.server().drain();
        assertTrue(received.stream().anyMatch(p -> p instanceof IPacket.ToClient.Join.Accepted));

        IPacket.ToClient.ChunkData data = received.stream()
                .filter(IPacket.ToClient.ChunkData.class::isInstance)
                .map(IPacket.ToClient.ChunkData.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(ONLY_CHUNK, data.pos());

        // The payload must reconstruct a Chunk, or the wire format and the disk format have
        // quietly diverged. Chunk.getBlock returns a Block, so this compares enum to enum.
        Chunk replica = new Chunk(data.blocks());
        assertEquals(Block.STONE, replica.getBlock(EDIT_X, EDIT_Y, EDIT_Z));
    }

    @Test
    void aBlockEditChangesTheServerAndIsToldToTheClient() {
        Fixture fixture = serverWithOneChunk();
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        fixture.server().accept(pair.client());

        pair.server().send(new IPacket.ToServer.Join.Request("ben", PROTOCOL_VERSION));
        fixture.server().tick();
        pair.server().drain();

        assertEquals(Block.STONE, blockInWorld(fixture.world()));

        pair.server()
                .send(new IPacket.ToServer.BlockEdit(
                        fixture.server().currentTick(), EDIT_X, EDIT_Y, EDIT_Z, Block.AIR, true));
        fixture.server().tick();

        // Half one: the server's own world actually changed.
        assertEquals(Block.AIR, blockInWorld(fixture.world()));

        // Half two: and the client was told. A server that applies edits and never announces them
        // passes the assertion above and leaves every other player looking at a stale world.
        IPacket.ToClient.BlockChanged changed = pair.server().drain().stream()
                .filter(IPacket.ToClient.BlockChanged.class::isInstance)
                .map(IPacket.ToClient.BlockChanged.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(Block.AIR, changed.block());
        assertEquals(EDIT_X, changed.x());
    }
}
