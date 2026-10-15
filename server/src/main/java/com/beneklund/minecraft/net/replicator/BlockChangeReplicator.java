package com.beneklund.minecraft.net.replicator;

import com.beneklund.minecraft.net.GameServer;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.PlayerSession;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends every edit applied this tick to each player who holds the chunk it landed in, not just the
 * player who made it. {@link GameServer} feeds it through {@link #record}; the list empties once
 * it has been sent.
 */
public class BlockChangeReplicator implements IReplicator {
    private final List<IPacket.ToClient.BlockChanged> changesThisTick = new ArrayList<>();

    public void record(IPacket.ToClient.BlockChanged change) {
        changesThisTick.add(change);
    }

    @Override
    public void replicate(long tick, List<PlayerSession> joined) {
        for (PlayerSession session : joined) {
            for (IPacket.ToClient.BlockChanged change : changesThisTick) {
                if (session.hasChunk(ChunkPos.containing(change.x(), change.z()))) session.queue(change);
            }
        }
        changesThisTick.clear();
    }
}
