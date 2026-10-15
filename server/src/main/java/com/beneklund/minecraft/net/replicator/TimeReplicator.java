package com.beneklund.minecraft.net.replicator;

import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.PlayerSession;
import com.beneklund.minecraft.world.sky.DayNightCycle;
import java.util.List;

/**
 * Keeps every client's sky clock from drifting: the server's time of day, broadcast every {@code
 * interval} ticks. The client advances its own clock in between, so this only corrects error.
 */
public class TimeReplicator implements IReplicator {
    private final DayNightCycle cycle;
    private final int interval;

    public TimeReplicator(DayNightCycle cycle, int interval) {
        if (interval <= 0) throw new IllegalArgumentException(String.format("interval must be positive: %d", interval));
        this.cycle = cycle;
        this.interval = interval;
    }

    @Override
    public void replicate(long tick, List<PlayerSession> joined) {
        if (tick == 0) return;
        if (tick % interval == 0) {
            for (PlayerSession session : joined) {
                session.queue(new IPacket.ToClient.TimeUpdate(cycle.timeOfDay()));
            }
        }
    }
}
