package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private long tick;
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final List<IPacket.ToServer> receivedPackets = new ArrayList<>();
    // What this player is owed, held until flush so nothing leaves the server mid-tick.
    private final List<IPacket.ToClient> outbox = new ArrayList<>();
    private boolean joined;
    // Where the player last reported being. The spawn until the client says otherwise.
    private PlayerState state;

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

    public void moved(PlayerState state) {
        this.state = state;
    }

    public PlayerState state() {
        return state;
    }

    /** The chunk containing the player's last reported position, or null before it has one. */
    public ChunkPos chunkPos() {
        return state == null ? null : ChunkPos.containing(state.x(), state.z());
    }

    public void consumedInput(long inputTick) {
        tick = inputTick;
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

    /**
     * Sends everything queued this tick, in queue order. {@code tick} is reserved for a {@code
     * PlayerUpdate} carrying the server's simulated position, which the server doesn't produce yet.
     */
    public void flush(long tick) {
        for (IPacket.ToClient packet : outbox) link.send(packet);
        outbox.clear();
    }
}
