package com.beneklund.minecraft.net.replicator;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.InJvmLink;
import com.beneklund.minecraft.net.PlayerSession;
import com.beneklund.minecraft.world.sky.DayNightCycle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeReplicatorTest {
    private static final int INTERVAL = 20;
    private static final float TIME = 0.3f;

    private final DayNightCycle cycle = new DayNightCycle(TIME, DayNightCycle.DayLength.DEFAULT);
    private final TimeReplicator replicator = new TimeReplicator(cycle, INTERVAL);

    /** A session and the far end of its link, where what the server sent can be read back. */
    private record Peer(PlayerSession session, InJvmLink.Pair link) {
        List<IPacket.ToClient> received() {
            session.flush();
            return link.server().drain();
        }
    }

    private static Peer peer(int playerId) {
        InJvmLink.Pair link = InJvmLink.connect(playerId);
        return new Peer(new PlayerSession(playerId, link.client()), link);
    }

    @Test
    void silentBetweenIntervals() {
        Peer peer = peer(1);
        for (long tick = 1; tick < INTERVAL; tick++) replicator.replicate(tick, List.of(peer.session()));

        assertTrue(peer.received().isEmpty());
    }

    @Test
    void sendsTheServerTimeOnTheInterval() {
        Peer peer = peer(1);
        replicator.replicate(INTERVAL, List.of(peer.session()));

        List<IPacket.ToClient> received = peer.received();
        assertEquals(1, received.size());
        assertEquals(TIME, ((IPacket.ToClient.TimeUpdate) received.get(0)).timeOfDay());
    }

    @Test
    void sendsOnEveryMultipleOfTheInterval() {
        Peer peer = peer(1);
        replicator.replicate(INTERVAL, List.of(peer.session()));
        replicator.replicate(INTERVAL * 2L, List.of(peer.session()));

        assertEquals(2, peer.received().size());
    }

    @Test
    void sendsToEveryJoinedSession() {
        Peer first = peer(1);
        Peer second = peer(2);
        replicator.replicate(INTERVAL, List.of(first.session(), second.session()));

        assertEquals(1, first.received().size());
        assertEquals(1, second.received().size());
    }

    @Test
    void skipsTheFirstTick() {
        Peer peer = peer(1);
        replicator.replicate(0, List.of(peer.session()));

        assertTrue(peer.received().isEmpty());
    }

    @Test
    void rejectsAnIntervalThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new TimeReplicator(cycle, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimeReplicator(cycle, -1));
    }
}
