package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.world.sky.DayNightCycle;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The authoritative clock: it advances by game time per tick, and clients are told what it reads. */
class ServerClockTest {
    private static final int TICKS_PER_SECOND = 20;
    private static final float DAY_SECONDS = DayNightCycle.DayLength.DEFAULT.getSeconds();

    @Test
    void timeUpdateCarriesTheClockAfterOneSecondOfTicks() {
        RoundTripTest.Fixture fixture = RoundTripTest.serverWithOneChunk();
        InJvmLink.Pair pair = join(fixture);
        // The join tick already ran, so the server is on tick 1 with one tick of time advanced.
        while (fixture.server().currentTick() < TICKS_PER_SECOND)
            fixture.server().tick();
        assertTrue(timeUpdates(pair.server().drain()).isEmpty(), "no update before the interval");

        fixture.server().tick();

        List<IPacket.ToClient.TimeUpdate> updates = timeUpdates(pair.server().drain());
        assertEquals(1, updates.size());
        // Twenty ticks of one twentieth of a second is one second of a 1200 second day.
        float expected = DayNightCycle.MORNING + 1f / DAY_SECONDS;
        assertEquals(expected, updates.get(0).timeOfDay(), 1e-6f);
    }

    @Test
    void acceptedCarriesTheClockAtJoin() {
        RoundTripTest.Fixture fixture = RoundTripTest.serverWithOneChunk();
        InJvmLink.Pair pair = join(fixture);

        // join() already drained nothing; the Accepted went out on the join tick.
        IPacket.Join.Accepted accepted = (IPacket.Join.Accepted) pair.server().drain().stream()
                .filter(IPacket.Join.Accepted.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(DayNightCycle.MORNING, accepted.cycleState().time(), 1e-6f);
        assertEquals(DayNightCycle.DayLength.DEFAULT, accepted.cycleState().duration());
    }

    private static InJvmLink.Pair join(RoundTripTest.Fixture fixture) {
        InJvmLink.Pair pair = InJvmLink.connect(1);
        fixture.server().accept(pair.client());
        pair.server().send(new IPacket.ToServer.Join.Request("ben", PacketCodec.PROTOCOL_VERSION));
        fixture.server().tick();
        return pair;
    }

    private static List<IPacket.ToClient.TimeUpdate> timeUpdates(List<IPacket.ToClient> packets) {
        return packets.stream()
                .filter(IPacket.ToClient.TimeUpdate.class::isInstance)
                .map(IPacket.ToClient.TimeUpdate.class::cast)
                .toList();
    }
}
