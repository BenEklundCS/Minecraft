package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerBody;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.*;
import org.joml.Vector3fc;

/**
 * The server's view of one connected client: its link, inbox and outbox, join state, last
 * reported position, and the set of chunks it has been sent.
 *
 * <p>The sent-chunk set is the server's model of the client's replica. {@link GameServer} consults
 * it to decide what to stream, what to unload, and which edits this player gets.
 */
public class PlayerSession {
    private final int playerId;
    private final IClientLink link;
    // The last input tick this player sent that the server has consumed. Goes back down as
    // PlayerUpdate's ackTick once the server simulates a body.
    private long lastInputTick = -1;
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final List<IPacket.ToServer> receivedPackets = new ArrayList<>();
    // What this player is owed, held until flush so nothing leaves the server mid-tick.
    private final List<IPacket.ToClient> outbox = new ArrayList<>();
    /** Inputs waiting for {@code simulate()} **/
    private final ArrayDeque<IPacket.ToServer.PlayerInput> inputs = new ArrayDeque<>();

    private boolean joined;
    // Set by a rejected join: flush sends the reason, then closes. Closing straight away would drop
    // the reason still sitting in the outbox.
    private boolean closeAfterFlush;
    // The server's simulation of this player. Null until joined.
    private PlayerBody body;

    public PlayerSession(int playerId, IClientLink link) {
        this.playerId = playerId;
        this.link = link;
    }

    public int playerId() {
        return playerId;
    }

    /**
     * Moves every packet waiting on the link into the inbox. Handling them happens later in the
     * tick, once every player has been drained, so one player's packets never act before another's
     * have arrived.
     */
    public void drainLink() {
        for (IPacket.ToServer packet : link.drain()) receive(packet);
    }

    public void receive(IPacket.ToServer packet) {
        receivedPackets.add(packet);
    }

    /** Returns everything received and empties the inbox, so each packet is applied exactly once. */
    public List<IPacket.ToServer> takeReceived() {
        List<IPacket.ToServer> taken = new ArrayList<>(receivedPackets);
        receivedPackets.clear();
        return taken;
    }

    public void queue(IPacket.ToClient packet) {
        outbox.add(packet);
    }

    public void markJoined() {
        joined = true;
    }

    public boolean isJoined() {
        return joined;
    }

    /** Creates the body at {@code at}. Called once, on join. */
    public void spawnAt(PlayerState at) {
        body = new PlayerBody(at);
    }

    public PlayerBody body() {
        return body;
    }

    public PlayerState state() {
        return body == null ? null : body.state();
    }

    /** The chunk containing the body, or null before join. */
    public ChunkPos chunkPos() {
        return body == null ? null : ChunkPos.containing(body.getPosition().x, body.getPosition().z);
    }

    public void enqueueInput(IPacket.ToServer.PlayerInput input) {
        inputs.add(input);
    }

    /** Returns every queued input, oldest first, and empties the queue. */
    public List<IPacket.ToServer.PlayerInput> takeInputs() {
        List<IPacket.ToServer.PlayerInput> taken = new ArrayList<>(inputs);
        inputs.clear();
        return taken;
    }

    public long lastInputTick() {
        return lastInputTick;
    }

    public void acked(long inputTick) {
        lastInputTick = inputTick;
    }

    /**
     * Records {@code pos} as sent.
     *
     * @return true the first time, false after; the check and the record are one {@code Set.add},
     *     so they can't drift apart
     */
    public boolean markChunkSent(ChunkPos pos) {
        return loadedChunks.add(pos);
    }

    /** Forgets {@code pos}, returning true if this player had it. */
    public boolean markChunkUnloaded(ChunkPos pos) {
        return loadedChunks.remove(pos);
    }

    public boolean hasChunk(ChunkPos pos) {
        return loadedChunks.contains(pos);
    }

    /** A snapshot of the sent chunks, so the caller can unload while iterating. */
    public List<ChunkPos> sentChunks() {
        return List.copyOf(loadedChunks);
    }

    public boolean isOpen() {
        return link.isOpen();
    }

    public void close() {
        link.close();
    }

    public void closeAfterFlush() {
        closeAfterFlush = true;
    }

    /**
     * Sends everything queued this tick, in queue order, then a {@code PlayerUpdate} with where the
     * body ended up. A joined player gets one every tick, input or not, so the client always has a
     * state to reconcile against.
     */
    public void flush() {
        if (joined && body != null) {
            Vector3fc p = body.getPosition();
            queue(new IPacket.ToClient.PlayerUpdate(
                    lastInputTick, p.x(), p.y(), p.z(), body.getVelocity().y, body.isOnGround()));
        }
        for (IPacket.ToClient packet : outbox) link.send(packet);
        outbox.clear();
        if (closeAfterFlush) link.close();
    }
}
