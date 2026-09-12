package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* A client session existing on the server and all its related state */
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

    // Drain phase. Only moves packets off the link and into the inbox; handling them is apply's
    // job, and doing it here would let one player's packets act before another's have arrived.
    public void drainLink() {
        for (IPacket.ToServer packet : link.drain()) receive(packet);
    }

    public void receive(IPacket.ToServer packet) {
        receivedPackets.add(packet);
    }

    // Hands over everything received and empties the inbox, so a packet is applied exactly once.
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

    public ChunkPos chunkPos() {
        return state == null ? null : ChunkPos.containing(state.x(), state.z());
    }

    public void consumedInput(long inputTick) {
        tick = inputTick;
    }

    // True the first time a chunk is offered, false every time after. Set.add already answers
    // "was this new?", so the check and the record can't drift apart.
    public boolean markChunkSent(ChunkPos pos) {
        return loadedChunks.add(pos);
    }

    // True if this player had it. Set.remove answers that the same way add answers "was it new".
    public boolean markChunkUnloaded(ChunkPos pos) {
        return loadedChunks.remove(pos);
    }

    public boolean hasChunk(ChunkPos pos) {
        return loadedChunks.contains(pos);
    }

    // A copy, so the caller can unload while iterating.
    public List<ChunkPos> sentChunks() {
        return List.copyOf(loadedChunks);
    }

    public boolean isOpen() {
        return link.isOpen();
    }

    public void close() {
        link.close();
    }

    // Flush phase. The tick goes unused until the server can send a PlayerUpdate, which needs a
    // position the server doesn't simulate yet.
    public void flush(long tick) {
        for (IPacket.ToClient packet : outbox) link.send(packet);
        outbox.clear();
    }
}
