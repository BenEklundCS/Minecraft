package com.beneklund.minecraft.platform.input;

import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// maps glfw keycodes to InputAction
public class InputMapper {
    public static final Map<Integer, InputAction> DEFAULT_BINDINGS = Map.ofEntries(
            // exit game
            Map.entry(GLFW_KEY_ESCAPE, InputAction.Simple.EXIT),
            Map.entry(GLFW_KEY_X, InputAction.Simple.EXIT),
            // player
            Map.entry(GLFW_KEY_1, InputAction.Simple.SLOT_1),
            Map.entry(GLFW_KEY_2, InputAction.Simple.SLOT_2),
            Map.entry(GLFW_KEY_3, InputAction.Simple.SLOT_3),
            Map.entry(GLFW_KEY_4, InputAction.Simple.SLOT_4),
            Map.entry(GLFW_KEY_5, InputAction.Simple.SLOT_5),
            Map.entry(GLFW_KEY_6, InputAction.Simple.SLOT_6),
            Map.entry(GLFW_KEY_7, InputAction.Simple.SLOT_7),
            Map.entry(GLFW_KEY_8, InputAction.Simple.SLOT_8),
            Map.entry(GLFW_KEY_9, InputAction.Simple.SLOT_9),
            Map.entry(GLFW_KEY_SPACE, InputAction.Simple.JUMP),
            Map.entry(GLFW_KEY_I, InputAction.Simple.INVENTORY),
            Map.entry(GLFW_KEY_F3, InputAction.Simple.DEBUG_OVERLAY),
            Map.entry(GLFW_KEY_P, InputAction.Simple.PAUSE),

            // mouse
            Map.entry(GLFW_MOUSE_BUTTON_1, InputAction.Simple.BREAK_BLOCK),
            Map.entry(GLFW_MOUSE_BUTTON_2, InputAction.Simple.PLACE_BLOCK));

    private final InputEventQueue queue;
    private final Map<Integer, InputAction> bindings;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public InputMapper(InputEventQueue queue) {
        this.queue = queue;
        this.bindings = DEFAULT_BINDINGS;
    }

    public InputMapper(InputEventQueue queue, Map<Integer, InputAction> bindings) {
        this.queue = queue;
        this.bindings = bindings;
    }

    public List<InputAction> drain() {
        List<InputAction> actions = new ArrayList<>();
        List<RawInputEvent> rawInputEvents = this.queue.drain();
        for (var rawInputEvent : rawInputEvents) {
            switch (rawInputEvent) {
                case RawInputEvent.KeyEvent e -> {
                    if (e.action() == GLFW_RELEASE) {
                        InputAction action = this.bindings.get(e.key());
                        if (action != null) actions.add(action);
                    }
                }
                case RawInputEvent.MouseButtonEvent e -> {
                    if (e.action() == GLFW_RELEASE) {
                        InputAction action = this.bindings.get(e.button());
                        if (action != null) actions.add(action);
                    }
                }
                case RawInputEvent.MouseMoveEvent e -> {
                    if (!Double.isNaN(this.lastMouseX)) {
                        float dx = (float) (e.xpos() - this.lastMouseX);
                        float dy = (float) (e.ypos() - this.lastMouseY);
                        actions.add(new InputAction.LookAction(dx, dy));
                    }
                    this.lastMouseX = e.xpos();
                    this.lastMouseY = e.ypos();
                }
                case RawInputEvent.ScrollEvent e -> actions.add(new InputAction.ScrollAction((float) e.yoffset()));
            }
        }
        return actions;
    }
}
