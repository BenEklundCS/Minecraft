package com.beneklund.minecraft.platform.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The buffer between GLFW's input callbacks and {@link InputMapper}, the first stage of the input
 * pipeline: GLFW callback, this queue, {@link InputMapper}, {@code List<IInputAction>}.
 *
 * <p>GLFW invokes callbacks inside {@code glfwPollEvents}, and {@code Game} drains straight after
 * polling, so both ends currently run on the main thread. The queue is lock-free, so it stays
 * correct if polling ever moves to its own thread.
 *
 * @see <a href="https://www.glfw.org/docs/latest/input_guide.html#events">GLFW: Event
 *     processing</a>
 */
public class InputEventQueue {
    private final ConcurrentLinkedQueue<IRawInputEvent> queue = new ConcurrentLinkedQueue<>();

    public void offer(IRawInputEvent event) {
        queue.offer(event);
    }

    /** Removes and returns every queued event, oldest first. */
    public List<IRawInputEvent> drain() {
        List<IRawInputEvent> batch = new ArrayList<>();
        IRawInputEvent e;
        while ((e = queue.poll()) != null) batch.add(e);
        return batch;
    }
}
