package com.beneklund.minecraft.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/*
Wraps a server link and delays sending packets to the client.
This allows unit-testing of server and client code on a delayed connection.
We delay only in one direction as this is enough to reproduce most bugs in game logic without making test results difficult to reason about.
 */
public class DelayedServerLink implements IServerLink {
    public static final long DEFAULT_DELAY_MS = 150;

    private record Held(long releaseAtMillis, IPacket.ToClient packet) {}
    ;

    private final IServerLink delegate;
    private final long delayMillis;
    private final LongSupplier nowMillis;
    private final Deque<Held> held = new ArrayDeque<>();

    public DelayedServerLink(IServerLink delegate, long delayMillis, LongSupplier nowMillis) {
        this.delegate = delegate;
        this.delayMillis = delayMillis;
        this.nowMillis = nowMillis;
    }

    @Override
    public void send(IPacket.ToServer packet) {
        delegate.send(packet);
    }

    @Override
    public List<IPacket.ToClient> drain() {
        List<IPacket.ToClient> out = delegate.drain();
        for (IPacket.ToClient packet : out) {
            held.add(new Held(nowMillis.getAsLong() + delayMillis, packet));
        }

        List<IPacket.ToClient> res = new ArrayList<>();
        while (held.peekFirst() != null && held.peekFirst().releaseAtMillis <= nowMillis.getAsLong()) {
            res.add(held.removeFirst().packet);
        }
        return res;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
