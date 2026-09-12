package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.ChunkState;
import com.beneklund.minecraft.world.IChunkStore;
import com.beneklund.minecraft.world.LightEngine;
import com.beneklund.minecraft.world.ServerWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.List;
import java.util.Optional;
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

    private static final PlayerState SPAWN = new PlayerState(8f, 65f, 8f, 0f, 0f);

    private static final IWorldGenerator NO_GENERATION = (pos, seed, chunk) -> {};
    private static final IChunkStore NO_DISK = new IChunkStore() {
        @Override
        public void save(ChunkPos pos, Chunk chunk) {}

        @Override
        public Optional<Chunk> load(ChunkPos pos) {
            return Optional.empty();
        }
    };
    private static final IPlayerStore NO_PLAYER_SAVES = new IPlayerStore() {
        @Override
        public void save(PlayerState state) {}

        @Override
        public Optional<PlayerState> load() {
            return Optional.empty();
        }
    };

    // The world is kept alongside the server because BlockDef carries no back-reference to its
    // Block — see the note under this block — so the exact assertion has to read the Chunk.
    private record Fixture(GameServer server, World world) {}

    private static Fixture serverWithOneChunk() {
        World world = new World(new ConcurrentHashMap<>());
        Chunk chunk = new Chunk();
        chunk.setBlock(EDIT_X, EDIT_Y, EDIT_Z, Block.STONE);
        world.addChunk(ONLY_CHUNK, chunk);
        // What a disk load does. The server only streams or edits a LIVE chunk.
        chunk.tryTransition(ChunkState.LIVE);

        BlockRegistry registry = BlockRegistry.createDefault();
        ServerWorldAuthority authority = new ServerWorldAuthority(world, registry, new LightEngine(registry));
        // Radius 0: only the player's own chunk is loaded or streamed.
        ServerChunkManager chunks = new ServerChunkManager(new WorldConfig(SEED, 0), world, NO_GENERATION, NO_DISK);
        GameServer server =
                new GameServer(world, authority, chunks, new RadiusChunkStreamer(0), NO_PLAYER_SAVES, SPAWN, SEED);
        return new Fixture(server, world);
    }

    private static Block blockInWorld(World world) {
        return world.getChunk(ONLY_CHUNK).getBlock(EDIT_X, EDIT_Y, EDIT_Z);
    }

    private static InJvmLink.Pair joined(Fixture fixture) {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        fixture.server().accept(pair.client());
        pair.server().send(new IPacket.ToServer.Join.Request("ben", PROTOCOL_VERSION));
        fixture.server().tick();
        return pair;
    }

    @Test
    void joinIsAcceptedAndTheChunkIsStreamed() {
        Fixture fixture = serverWithOneChunk();
        InJvmLink.Pair pair = joined(fixture);

        List<IPacket.ToClient> received = pair.server().drain();
        IPacket.Join.Accepted accepted = received.stream()
                .filter(IPacket.Join.Accepted.class::isInstance)
                .map(IPacket.Join.Accepted.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(SPAWN, accepted.spawn());

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
        InJvmLink.Pair pair = joined(fixture);
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

    @Test
    void movingAwayUnloadsTheChunk() {
        Fixture fixture = serverWithOneChunk();
        InJvmLink.Pair pair = joined(fixture);
        pair.server().drain();

        pair.server().send(new IPacket.ToServer.PlayerPosition(200f, 65f, 200f, 0f, 0f));
        fixture.server().tick();

        assertTrue(pair.server().drain().contains(new IPacket.ToClient.ChunkUnload(ONLY_CHUNK)));
    }
}
