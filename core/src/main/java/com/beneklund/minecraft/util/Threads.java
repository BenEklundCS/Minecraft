package com.beneklund.minecraft.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread helpers shared by the worker pools. */
public final class Threads {
    private Threads() {}

    /**
     * A factory for daemon threads named by {@code pattern}, which takes one {@code %d} for a
     * counter starting at 0. Daemon threads let the JVM exit without the pools being shut down
     * first.
     */
    public static ThreadFactory namedFactory(String pattern) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, pattern.formatted(n.getAndIncrement()));
            t.setDaemon(true);
            return t;
        };
    }
}
