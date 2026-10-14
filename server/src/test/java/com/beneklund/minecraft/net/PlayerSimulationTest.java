package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.MovementTuning;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.util.FixedTimestep;
import com.beneklund.minecraft.world.ServerWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.chunk.ChunkState;
import com.beneklund.minecraft.world.chunk.IChunkStore;
import com.beneklund.minecraft.world.chunk.LightEngine;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlayerSimulationTest {
    private static final int PLAYER_ID = 1;
    private static final int PROTOCOL_VERSION = PacketCodec.PROTOCOL_VERSION;
    private static final long SEED = 42L;

    // Same origin chunk as RoundTripTest, so local and world coordinates coincide for x and z.
    private static final ChunkPos ONLY_CHUNK = new ChunkPos(0, 0);
    private static final int FLOOR_Y = 64;
    // Feet on the floor's top face, well inside the chunk so six walking steps stay in it.
    private static final PlayerState SPAWN = new PlayerState(8f, FLOOR_Y + 1, 8f, 0f, 0f);

    private static final float WALK = MovementTuning.DEFAULT.walkSpeed();
    private static final float STEP = FixedTimestep.STEP_SECONDS;

    private static final IWorldGenerator NO_GENERATION = (pos, seed, chunk) -> {};
    private static final IChunkStore NO_DISK = new IChunkStore() {
        @Override
        public void save(ChunkPos pos, Chunk chunk) {}

        @Override
        public Optional<Chunk> load(ChunkPos pos) {
            return Optional.empty();
        }
    };

    private static final class RecordingPlayerStore implements IPlayerStore {
        PlayerState saved;

        @Override
        public void save(PlayerState state) {
            saved = state;
        }

        @Override
        public Optional<PlayerState> load() {
            return Optional.empty();
        }
    }

    /*
     * RoundTripTest's one-chunk server with a stone floor across the whole chunk. With live=false
     * the chunk is left UNLOADED: ServerChunkManager skips a chunk World already holds, so nothing
     * generates it and it stays unreplicable for the whole test.
     */
    private static GameServer server(boolean live, IPlayerStore store) {
        World world = new World(new ConcurrentHashMap<>());
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) chunk.setBlock(x, FLOOR_Y, z, Block.STONE);
        }
        world.addChunk(ONLY_CHUNK, chunk);
        if (live) chunk.tryTransition(ChunkState.LIVE);

        BlockRegistry registry = BlockRegistry.createDefault();
        ServerWorldAuthority authority = new ServerWorldAuthority(world, registry, new LightEngine(registry));
        ServerChunkManager chunks = new ServerChunkManager(new WorldConfig(SEED, 0), world, NO_GENERATION, NO_DISK);
        PlayerMovement movement = new PlayerMovement(new Physics(), MovementTuning.DEFAULT);
        return new GameServer(world, authority, chunks, new RadiusChunkStreamer(0), store, SPAWN, movement, SEED);
    }

    private static GameServer server() {
        return server(true, new RecordingPlayerStore());
    }

    // Joins, runs the join tick, and throws away everything it sent, so each test sees only the
    // packets from its own ticks.
    private static InJvmLink.Pair joined(GameServer server) {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        server.accept(pair.client());
        pair.server().send(new IPacket.ToServer.Join.Request("ben", PROTOCOL_VERSION));
        server.tick();
        pair.server().drain();
        return pair;
    }

    private static IPacket.ToServer.PlayerInput walk(long inputTick) {
        return new IPacket.ToServer.PlayerInput(inputTick, new PlayerIntent(0, 1, false, false, false, 0, 0));
    }

    private static List<IPacket.ToClient.PlayerUpdate> updates(InJvmLink.Pair pair) {
        return pair.server().drain().stream()
                .filter(IPacket.ToClient.PlayerUpdate.class::isInstance)
                .map(IPacket.ToClient.PlayerUpdate.class::cast)
                .toList();
    }

    private static IPacket.ToClient.PlayerUpdate lastUpdate(InJvmLink.Pair pair) {
        return updates(pair).getLast();
    }

    @Test
    void walkingInputsMoveTheServerBody() {
        GameServer server = server();
        InJvmLink.Pair pair = joined(server);

        for (int i = 0; i < 6; i++) pair.server().send(walk(i));
        server.tick();

        IPacket.ToClient.PlayerUpdate update = lastUpdate(pair);
        assertEquals(5, update.ackTick());
        assertEquals(SPAWN.z() + 6 * WALK * STEP, update.z(), 1e-5);
    }

    // The client needs an update every tick to reconcile against, whether or not it moved.
    // ackTick -1 means no input consumed yet.
    @Test
    void updateIsSentEveryTickEvenWithNoInput() {
        GameServer server = server();
        InJvmLink.Pair pair = joined(server);

        server.tick();
        server.tick();

        List<IPacket.ToClient.PlayerUpdate> updates = updates(pair);
        assertEquals(2, updates.size());
        assertEquals(-1, updates.get(0).ackTick());
        assertEquals(-1, updates.get(1).ackTick());
    }

    // A resent input must not step the body twice, or a client that retransmits walks faster.
    @Test
    void duplicateInputTickIsIgnored() {
        GameServer server = server();
        InJvmLink.Pair pair = joined(server);

        pair.server().send(walk(0));
        pair.server().send(walk(0));
        server.tick();

        IPacket.ToClient.PlayerUpdate update = lastUpdate(pair);
        assertEquals(0, update.ackTick());
        assertEquals(SPAWN.z() + WALK * STEP, update.z(), 1e-5, "one step, not two");
    }

    // Before the chunk is LIVE the server's world reads air there, so stepping would drop the
    // body through the floor. The server acks the inputs as no-ops instead: the queue stays short
    // and the client is told it didn't move.
    @Test
    void walkingInputWhileChunkNotLiveIsAckedButDoesNotMove() {
        GameServer server = server(false, new RecordingPlayerStore());
        InJvmLink.Pair pair = joined(server);

        for (int i = 0; i < 3; i++) pair.server().send(walk(i));
        server.tick();

        IPacket.ToClient.PlayerUpdate update = lastUpdate(pair);
        assertEquals(2, update.ackTick());
        assertEquals(SPAWN.x(), update.x());
        assertEquals(SPAWN.y(), update.y());
        assertEquals(SPAWN.z(), update.z());
    }

    // The save comes from the body the server simulated. The client never reports a position.
    @Test
    void disconnectSavesTheSimulatedPosition() {
        RecordingPlayerStore store = new RecordingPlayerStore();
        GameServer server = server(true, store);
        InJvmLink.Pair pair = joined(server);

        for (int i = 0; i < 6; i++) pair.server().send(walk(i));
        server.tick();
        pair.server().send(new IPacket.ToServer.Disconnect("quit"));
        server.tick();

        assertNotNull(store.saved, "disconnect saved the player");
        assertEquals(SPAWN.z() + 6 * WALK * STEP, store.saved.z(), 1e-5, "saved where the body walked to, not spawn");
    }
}
