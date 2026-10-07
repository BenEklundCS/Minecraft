package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.MovementTuning;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.PlayerBody;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.IWorldView;
import com.beneklund.minecraft.world.ServerWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.chunk.ChunkState;
import com.beneklund.minecraft.world.chunk.IChunkStore;
import com.beneklund.minecraft.world.chunk.LightEngine;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A real {@link GameServer} and a real {@link PlayerPrediction} with 150 ms between them, on a
 * clock the test advances by hand. No sleeps and no wall clock, so every run is the same run.
 */
class PredictionTest {
    private static final int PLAYER_ID = 1;
    private static final int PROTOCOL_VERSION = PacketCodec.PROTOCOL_VERSION;
    private static final long SEED = 42L;
    private static final long DELAY_MS = 150;
    private static final long FRAME_MS = 17;
    // 60 Hz client steps against a 20 Hz server tick.
    private static final int FRAMES_PER_TICK = 3;

    // Origin chunk, so local and world coordinates coincide. Spawn sits low in z so two seconds of
    // walking reaches the wall at z=12 without the server body leaving the chunk.
    private static final ChunkPos ONLY_CHUNK = new ChunkPos(0, 0);
    private static final int FLOOR_Y = 64;
    private static final int WALL_Z = 12;
    private static final PlayerState SPAWN = new PlayerState(8f, FLOOR_Y + 1, 4f, 0f, 0f);

    private static final PlayerIntent WALK = new PlayerIntent(0, 1, false, false, false, 0, 0);
    private static final PlayerIntent JUMP = new PlayerIntent(0, 1, true, false, false, 0, 0);

    private static final BlockRegistry REGISTRY = BlockRegistry.createDefault();

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

    // The client's copy of the world: the same stone floor as the server, and nothing else.
    private static final IWorldView CLIENT_FLOOR = new IWorldView() {
        @Override
        public BlockDef getBlock(int x, int y, int z) {
            return REGISTRY.get(y <= FLOOR_Y ? Block.STONE : Block.AIR);
        }

        @Override
        public Chunk getChunk(ChunkPos pos) {
            return null;
        }

        @Override
        public List<Entity> getEntities(AABB aabb) {
            return List.of();
        }
    };

    private static GameServer server(boolean wall) {
        World world = new World(new ConcurrentHashMap<>());
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= FLOOR_Y; y++) chunk.setBlock(x, y, z, Block.STONE);
            }
        }
        if (wall) {
            for (int x = 0; x < 16; x++) {
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) chunk.setBlock(x, y, WALL_Z, Block.STONE);
            }
        }
        world.addChunk(ONLY_CHUNK, chunk);
        chunk.tryTransition(ChunkState.LIVE);

        ServerWorldAuthority authority = new ServerWorldAuthority(world, REGISTRY, new LightEngine(REGISTRY));
        ServerChunkManager chunks = new ServerChunkManager(new WorldConfig(SEED, 0), world, NO_GENERATION, NO_DISK);
        PlayerMovement movement = new PlayerMovement(new Physics(), MovementTuning.DEFAULT);
        return new GameServer(
                world, authority, chunks, new RadiusChunkStreamer(0), NO_PLAYER_SAVES, SPAWN, movement, SEED);
    }

    /** One client: its body, its prediction, and its end of a delayed link. */
    private static final class Client {
        final AtomicLong clock = new AtomicLong();
        final PlayerBody body = new PlayerBody(SPAWN);
        final PlayerPrediction prediction =
                new PlayerPrediction(new PlayerMovement(new Physics(), MovementTuning.DEFAULT));
        final GameServer server;
        final IServerLink link;
        // How far each reconcile moved the body: position after snap and replay minus before.
        final List<Float> corrections = new ArrayList<>();
        IPacket.ToClient.PlayerUpdate lastUpdate;
        float maxZ = Float.NEGATIVE_INFINITY;
        int frame;

        Client(GameServer server) {
            this.server = server;
            InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
            server.accept(pair.client());
            link = new DelayedServerLink(pair.server(), DELAY_MS, clock::get);
            link.send(new IPacket.ToServer.Join.Request("ben", PROTOCOL_VERSION));
            server.tick();
        }

        /** One client frame: advance time, predict one step (or none), tick on schedule, reconcile. */
        void frame(PlayerIntent intent) {
            clock.addAndGet(FRAME_MS);
            if (intent != null) link.send(prediction.step(body, CLIENT_FLOOR, intent));
            if (++frame % FRAMES_PER_TICK == 0) server.tick();
            for (IPacket.ToClient packet : link.drain()) {
                if (packet instanceof IPacket.ToClient.PlayerUpdate update) {
                    Vector3f before = new Vector3f(body.getPosition());
                    prediction.reconcile(body, CLIENT_FLOOR, update);
                    corrections.add(before.distance(body.getPosition()));
                    lastUpdate = update;
                }
            }
            maxZ = Math.max(maxZ, body.getPosition().z);
        }

        /** Stops stepping and lets the server catch up until every input is acked. */
        void settle() {
            for (int i = 0; i < 200 && prediction.pending() > 0; i++) frame(null);
            assertEquals(0, prediction.pending(), "every input acked");
        }
    }

    // The invariant that makes reconciliation invisible: when both sides agree on the world, the
    // server's state at ackTick is exactly what the client predicted there, so snapping and
    // replaying lands the body where it already was. Every correction is 0, not merely small.
    @Test
    void agreeingWorldsConvergeExactly() {
        Client client = new Client(server(false));

        for (int i = 0; i < 40; i++) client.frame(WALK);
        client.frame(JUMP);
        for (int i = 0; i < 60; i++) client.frame(WALK);
        client.settle();

        assertTrue(client.corrections.size() > 20, "updates arrived through the delay");
        assertTrue(client.corrections.stream().allMatch(d -> d == 0f), "corrections " + client.corrections);
        Vector3f p = client.body.getPosition();
        assertEquals(new Vector3f(client.lastUpdate.x(), client.lastUpdate.y(), client.lastUpdate.z()), p);
        assertTrue(p.z > SPAWN.z() + 5, "the body walked");
    }

    // The whole card in one assertion: the client believed something the server didn't (no
    // wall), walked through it, and once the updates landed, replay put it where the server says.
    @Test
    void serverOnlyWallStopsTheClient() {
        Client client = new Client(server(true));

        for (int i = 0; i < 120; i++) client.frame(WALK);
        client.settle();

        assertTrue(client.maxZ > WALL_Z, "the client walked through the wall it can't see");
        assertEquals(WALL_Z - PlayerBody.DEPTH / 2, client.body.getPosition().z, 1e-5);
    }
}
