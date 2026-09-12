package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InJvmLinkTest {
    private static final int PLAYER_ID = 1;

    @Test
    void packetsArriveAtTheOtherEndInSendOrder() {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);

        pair.server().send(new IPacket.ToServer.Join.Request("ben", 1));
        pair.server().send(new IPacket.ToServer.Disconnect("done"));

        List<IPacket.ToServer> received = pair.client().drain();
        assertEquals(2, received.size());
        assertTrue(received.get(0) instanceof IPacket.ToServer.Join.Request);
        assertTrue(received.get(1) instanceof IPacket.ToServer.Disconnect);
    }

    // The name is drain, not peek. A second call must come back empty or every packet is applied
    // twice - which on an input packet means the player moves twice as fast as they asked to.
    @Test
    void drainEmptiesTheQueue() {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);
        pair.server().send(new IPacket.ToServer.Join.Request("ben", 1));

        assertEquals(1, pair.client().drain().size());
        assertEquals(List.of(), pair.client().drain());
    }

    @Test
    void theTwoDirectionsDoNotShareAQueue() {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);

        pair.server().send(new IPacket.ToServer.Join.Request("ben", 1));

        // The sender must not receive its own packet back.
        assertEquals(List.of(), pair.server().drain());
        assertEquals(1, pair.client().drain().size());
    }

    @Test
    void closingEitherEndClosesBothAndDropsLaterSends() {
        InJvmLink.Pair pair = InJvmLink.connect(PLAYER_ID);

        pair.client().close();

        assertFalse(pair.server().isOpen());
        assertFalse(pair.client().isOpen());
        pair.server().send(new IPacket.ToServer.Join.Request("ben", 1));
        assertEquals(List.of(), pair.client().drain());
    }
}
