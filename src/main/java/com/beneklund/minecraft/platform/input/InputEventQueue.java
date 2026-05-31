package com.beneklund.minecraft.platform.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

// Bridge between the GLFW callback thread (offers) and the game-update thread (drains).
// ConcurrentLinkedQueue is lock-free, so the callback never blocks the main thread.
public class InputEventQueue {
    private final ConcurrentLinkedQueue<RawInputEvent> queue = new ConcurrentLinkedQueue<>();

    public void offer(RawInputEvent event) {
        this.queue.offer(event);
    }

    public List<RawInputEvent> drain() {
        List<RawInputEvent> batch = new ArrayList<>();
        RawInputEvent e;
        while ((e = this.queue.poll()) != null) batch.add(e);
        return batch;
    }
}
