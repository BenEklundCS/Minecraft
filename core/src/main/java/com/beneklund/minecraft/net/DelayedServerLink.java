package com.beneklund.minecraft.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * An {@link IServerLink} that holds every server-to-client packet for a fixed delay, for testing
 * client and server logic against latency.
 *
 * <p>Only the downward direction is delayed, which reproduces most latency bugs in game logic
 * while keeping test timelines easy to reason about. Packets are released in order, never
 * reordered or dropped.
 *
 * <p>The clock is injected, so tests advance time by hand. A packet's delay starts when {@link
 * #drain()} first pulls it from the wrapped link, so the client has to keep polling.
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
