package com.beneklund.minecraft.platform.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

// Bridge between the GLFW callback thread (offers) and the game-update thread (drains).
// ConcurrentLinkedQueue is lock-free, so the callback never blocks the main thread.
public class InputEventQueue {
    private final ConcurrentLinkedQueue<IRawInputEvent> queue = new ConcurrentLinkedQueue<>();

    public void offer(IRawInputEvent event) {
        this.queue.offer(event);
    }

    public List<IRawInputEvent> drain() {
        List<IRawInputEvent> batch = new ArrayList<>();
        IRawInputEvent e;
        while ((e = this.queue.poll()) != null) batch.add(e);
        return batch;
    }
}
