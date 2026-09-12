package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DelayedServerLinkTest {
    private static final int PLAYER_ID = 1;

    private static final class TestClock {
        private long millis;

        long now() {
            return millis;
        }

        void advanceTo(long target) {
            millis = target;
        }
    }

    @Test
    void aPacketIsWithheldUntilTheDeadlineAndThenDelivered() {
        TestClock clock = new TestClock();
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        DelayedServerLink delayed =
                new DelayedServerLink(pair.server(), DelayedServerLink.DEFAULT_DELAY_MS, clock::now);

        pair.client().send(new IPacket.ToClient.PlayerDisconnected(2));

        // t=0: taken from the delegate, held, not due.
        assertEquals(List.of(), delayed.drain());

        // t=149: still not due.
        clock.advanceTo(DelayedServerLink.DEFAULT_DELAY_MS - 1);
        assertEquals(List.of(), delayed.drain());

        // t=150: due.
        clock.advanceTo(DelayedServerLink.DEFAULT_DELAY_MS);
        assertEquals(1, delayed.drain().size());
    }

    @Test
    void upwardPacketsAreNotDelayed() {
        TestClock clock = new TestClock();
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        DelayedServerLink delayed =
                new DelayedServerLink(pair.server(), DelayedServerLink.DEFAULT_DELAY_MS, clock::now);

        delayed.send(new IPacket.ToServer.Join.Request("ben", 1));

        assertEquals(1, pair.client().drain().size());
    }
}
