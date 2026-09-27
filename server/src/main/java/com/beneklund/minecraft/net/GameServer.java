package com.beneklund.minecraft.net;

import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The authoritative game simulation: owns the world, applies what clients ask for, and streams
 * the result back.
 *
 * <p>Clients never write the world. They send {@link IPacket.ToServer} requests; the server
 * validates each one, applies it, and tells every affected client what changed. The client's
 * replica holds only what arrived as packets.
 *
 * <p>{@link #tick()} runs on the {@code server-tick} thread at 20 Hz, in fixed phases:
 *
 * <ol>
 *   <li>drain every link into its session's inbox
 *   <li>apply each session's packets, then drop sessions whose link closed
 *   <li>tick the {@link ServerChunkManager} around the load centre
 *   <li>per session: stream chunk unloads, chunk data and this tick's edits, then flush
 * </ol>
 *
 * <p>Draining every player before applying anything means packet handling never depends on
 * which player's link was read first. Nothing leaves the server until the flush phase.
 *
 * @see <a href="https://www.gabrielgambetta.com/client-server-game-architecture.html">Gabriel
 *     Gambetta: Client-Server Game Architecture</a>
 * @see <a href="https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking">Valve:
 *     Source Multiplayer Networking</a>
 */
public class GameServer implements IGameServer {
    private final World world;
    private final IWorldAuthority authority;
    private final ServerChunkManager chunks;
    private final IChunkStreamer streamer;
    private final IPlayerStore playerStore;
    private final PlayerState spawn;
    private final PlayerMovement movement;
    private final long seed;
    private long tick = 0;
    private final List<PlayerSession> players = new ArrayList<>();
    // Every edit applied this tick. Filled by apply, sent to every joined player during flush (not
    // just the one who made the edit), then cleared.
    private final List<IPacket.ToClient.BlockChanged> changesThisTick = new ArrayList<>();

    public GameServer(
            World world,
            IWorldAuthority authority,
            ServerChunkManager chunks,
            IChunkStreamer streamer,
            IPlayerStore playerStore,
            PlayerState spawn,
            PlayerMovement movement,
            long seed) {
        this.world = world;
        this.authority = authority;
        this.chunks = chunks;
        this.streamer = streamer;
        this.playerStore = playerStore;
        this.spawn = spawn;
        this.movement = movement;
        this.seed = seed;
    }

    @Override
    public void accept(IClientLink client) {
        players.add(new PlayerSession(client.playerId(), client));
    }

    /** Runs one server tick. See the class comment for the phase order. */
    public void tick() {
        players.forEach(PlayerSession::drainLink);
        players.forEach(this::apply);
        players.removeIf(session -> !session.isOpen());
        players.forEach(this::simulate);
        ChunkPos center = loadCenter();
        if (center != null) chunks.tick(center);
        players.forEach(session -> {
            stream(session);
            session.flush();
        });
        changesThisTick.clear();
        tick++;
    }

    private void simulate(PlayerSession session) {
        if (!session.isJoined()) return;
        for (IPacket.ToServer.PlayerInput input : session.takeInputs()) {
            if (input.tick() <= session.lastInputTick()) continue;
            session.body().setOrientation(input.intent().pitch(), input.intent().yaw());
            if (input.intent().flying() || chunks.replicable(session.chunkPos()))
                movement.step(session.body(), authority, input.intent());
            session.acked(input.tick());
        }
    }

    @Override
    public long currentTick() {
        return tick;
    }

    /** Saves every joined player still connected. Players who disconnected were saved on leaving. */
    public void saveConnectedPlayers() {
        players.forEach(this::save);
    }

    private void apply(PlayerSession session) {
        for (IPacket.ToServer packet : session.takeReceived()) {
            switch (packet) {
                case IPacket.Join.Request request -> join(session);
                case IPacket.ToServer.BlockEdit edit -> applyEdit(session, edit);
                case IPacket.ToServer.PlayerInput input -> session.enqueueInput(input);
                case IPacket.ToServer.Disconnect disconnect -> {
                    save(session);
                    session.close();
                }
            }
        }
    }

    /**
     * Accepts a join and places the player at spawn, so chunks start loading there before the
     * client reports a position.
     */
    private void join(PlayerSession session) {
        session.markJoined();
        session.spawnAt(spawn);
        session.queue(new IPacket.Join.Accepted(session.playerId(), seed, tick, spawn));
    }

    private void save(PlayerSession session) {
        if (session.isJoined()) playerStore.save(session.state());
    }

    /** The chunk the load radius centres on: the first joined player's, or null before anyone joins. */
    private ChunkPos loadCenter() {
        for (PlayerSession session : players) {
            if (session.isJoined()) return session.chunkPos();
        }
        return null;
    }

    /**
     * Applies a client's block edit if the server agrees, and records it for broadcast.
     *
     * <p>The edit is accepted when the player has joined, {@code y} is inside the world's height,
     * and the target chunk was sent to this player and is still {@code LIVE} here. {@code
     * authority.setBlock} returns silently on a bad target, so without these checks a refused edit
     * would still go out as a {@code BlockChanged}.
     */
    private void applyEdit(PlayerSession session, IPacket.ToServer.BlockEdit edit) {
        if (!session.isJoined() || !Chunk.inYRange(edit.y())) return;
        ChunkPos pos = ChunkPos.containing(edit.x(), edit.z());
        if (!session.hasChunk(pos) || !chunks.replicable(pos)) return;
        authority.setBlock(edit.x(), edit.y(), edit.z(), edit.block());
        changesThisTick.add(new IPacket.ToClient.BlockChanged(edit.x(), edit.y(), edit.z(), edit.block()));
    }

    /**
     * Queues this tick's world traffic for one player: unloads, then chunk data, then edits, so an
     * edit never arrives for a chunk the client lacks.
     *
     * <p>A chunk is sent once, the first tick it is both {@code LIVE} and allowed by the {@link
     * IChunkStreamer}. The {@code replicable} check comes before {@code markChunkSent} because a
     * generating chunk is still air, and marking it sent would stop the real one from ever going
     * out.
     */
    private void stream(PlayerSession session) {
        if (!session.isJoined()) return;
        PlayerState player = session.state();
        for (ChunkPos pos : session.sentChunks()) {
            if (streamer.allowed(player, pos) && world.hasChunk(pos)) continue;
            session.markChunkUnloaded(pos);
            session.queue(new IPacket.ToClient.ChunkUnload(pos));
        }
        for (Map.Entry<ChunkPos, Chunk> entry : world.getChunkEntries()) {
            ChunkPos pos = entry.getKey();
            if (!chunks.replicable(pos) || !streamer.allowed(player, pos)) continue;
            if (session.markChunkSent(pos)) {
                // serialize() is the copy: a fresh byte[], never the server's Chunk.
                session.queue(
                        new IPacket.ToClient.ChunkData(pos, entry.getValue().serialize()));
            }
        }
        for (IPacket.ToClient.BlockChanged change : changesThisTick) {
            if (session.hasChunk(ChunkPos.containing(change.x(), change.z()))) session.queue(change);
        }
    }
}
