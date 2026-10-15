package com.beneklund.minecraft.net.replicator;

import com.beneklund.minecraft.net.GameServer;
import com.beneklund.minecraft.net.PlayerSession;
import java.util.List;

/**
 * One stream of server-to-client traffic: what it sends, to whom, and on which ticks. {@link
 * GameServer} calls every replicator every tick, in registration order, after the simulation has
 * run and before the sessions flush. Each decides for itself whether it owes anyone anything this
 * tick (every tick, every N ticks, only when something changed).
 *
 * <p>Registration order is packet order within a tick: chunk data has to be queued before the
 * edits that land in it.
 */
public interface IReplicator {
    /**
     * Queues this stream's packets for the players who should get them.
     *
     * @param tick the tick that is about to finish
     * @param joined every session that has joined; sessions still handshaking are left out
     */
    void replicate(long tick, List<PlayerSession> joined);
}
