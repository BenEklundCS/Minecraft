package com.beneklund.minecraft.net;

import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.net.replicator.IReplicator;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import com.beneklund.minecraft.world.sky.DayNightCycle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

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
 *   <li>adopt links handed over by {@link #accept} since the last tick
 *   <li>drain every link into its session's inbox
 *   <li>apply each session's packets, then save and drop sessions whose link closed
 *   <li>tick the {@link ServerChunkManager} around the load centre
 *   <li>run every {@link IReplicator} in registration order, then flush each session
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
    private final IWorldAuthority authority;
    private final ServerChunkManager chunks;
    private final IPlayerStore playerStore;
    private final PlayerState spawn;
    private final PlayerMovement movement;
    private final long seed;
    private final DayNightCycle cycle;
    private final float tickSeconds;
    private long tick = 0;
    private final ConcurrentLinkedQueue<IClientLink> pending = new ConcurrentLinkedQueue<>();
    private final List<PlayerSession> players = new ArrayList<>();
    private final Consumer<IPacket.ToClient.BlockChanged> editSink;
    private final List<IReplicator> replicators;

    public GameServer(
            IWorldAuthority authority,
            ServerChunkManager chunks,
            IPlayerStore playerStore,
            PlayerState spawn,
            PlayerMovement movement,
            long seed,
            DayNightCycle cycle,
            float tickSeconds,
            Consumer<IPacket.ToClient.BlockChanged> editSink,
            List<IReplicator> replicators) {
        this.authority = authority;
        this.chunks = chunks;
        this.playerStore = playerStore;
        this.spawn = spawn;
        this.movement = movement;
        this.seed = seed;
        this.cycle = cycle;
        this.tickSeconds = tickSeconds;
        this.editSink = editSink;
        this.replicators = List.copyOf(replicators);
    }

    @Override
    public void accept(IClientLink client) {
        pending.add(client);
    }

    /** Runs one server tick. See the class comment for the phase order. */
    public void tick() {
        connectPendingClients();
        players.forEach(PlayerSession::drainLink);
        players.forEach(this::apply);
        dropClosedSessions();
        players.forEach(this::simulate);
        List<ChunkPos> centers = players.stream()
                .filter(PlayerSession::isJoined)
                .map(PlayerSession::chunkPos)
                .toList();
        chunks.tick(centers);
        List<PlayerSession> joined =
                players.stream().filter(PlayerSession::isJoined).toList();
        replicators.forEach(replicator -> replicator.replicate(tick, joined));
        players.forEach(PlayerSession::flush);
        cycle.advance(tickSeconds);
        tick++;
    }

    private void connectPendingClients() {
        IClientLink client;
        while ((client = pending.poll()) != null) {
            players.add(new PlayerSession(client.playerId(), client));
        }
    }

    private void dropClosedSessions() {
        Iterator<PlayerSession> iterator = players.iterator();
        while (iterator.hasNext()) {
            PlayerSession next = iterator.next();
            if (!next.isOpen()) {
                save(next);
                iterator.remove();
            }
        }
    }

    /** Closes every link so each client sees a clean close. Only once the tick thread has stopped. */
    public void closeAllLinks() {
        players.forEach(PlayerSession::close);
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
                case IPacket.Join.Request request -> join(session, request);
                case IPacket.ToServer.BlockEdit edit -> applyEdit(session, edit);
                case IPacket.ToServer.PlayerInput input -> session.enqueueInput(input);
                case IPacket.ToServer.Disconnect disconnect -> {
                    session.close();
                }
            }
        }
    }

    /**
     * Accepts a join and places the player at spawn, so chunks start loading there before the
     * client reports a position. A client on an unsupported protocol version gets a {@code
     * Rejected} with the supported range, and its link closes after that tick's flush.
     *
     * <p>A request from a session that has already joined is ignored. Answering it would put the
     * player back at spawn, and kicking them would turn a client's double send into a disconnect.
     */
    private void join(PlayerSession session, IPacket.Join.Request request) {
        if (session.isJoined()) return;
        if (!PacketCodec.isCompatible(request.protocolVersion())) {
            session.queue(new IPacket.Join.Rejected(String.format(
                    "Protocol %d unsupported, server speaks %d to %d",
                    request.protocolVersion(), PacketCodec.MIN_COMPATIBLE_VERSION, PacketCodec.PROTOCOL_VERSION)));
            session.closeAfterFlush();
            return;
        }
        session.markJoined();
        session.spawnAt(spawn);
        session.queue(new IPacket.Join.Accepted(session.playerId(), seed, tick, cycle.getCycleState(), spawn));
    }

    private void save(PlayerSession session) {
        if (session.isJoined()) playerStore.save(session.state());
    }

    /** The chunk the load radius centres on: the first joined player's, or null before anyone joins. */
    private List<ChunkPos> loadCenters() {
        return players.stream()
                .filter(PlayerSession::isJoined)
                .map(PlayerSession::chunkPos)
                .toList();
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
        editSink.accept(new IPacket.ToClient.BlockChanged(edit.x(), edit.y(), edit.z(), edit.block()));
    }
}
