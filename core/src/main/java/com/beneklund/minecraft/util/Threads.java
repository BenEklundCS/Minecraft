package com.beneklund.minecraft.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Threads {
    private Threads() {}

    public static ThreadFactory namedFactory(String pattern) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, pattern.formatted(n.getAndIncrement()));
            t.setDaemon(true);
            return t;
        };
    }
}
