package com.beneklund.minecraft.platform.input;

import static org.lwjgl.glfw.GLFW.*;

import com.beneklund.minecraft.input.IInputAction;
import java.util.*;

// maps glfw keycodes to InputAction
public class InputMapper {
    public static final Map<Integer, IInputAction> DEFAULT_BINDINGS = Map.ofEntries(
            // exit game
            Map.entry(GLFW_KEY_ESCAPE, IInputAction.Simple.EXIT),
            Map.entry(GLFW_KEY_X, IInputAction.Simple.EXIT),
            // player
            Map.entry(GLFW_KEY_1, new IInputAction.HotbarActionI.Select(0)),
            Map.entry(GLFW_KEY_2, new IInputAction.HotbarActionI.Select(1)),
            Map.entry(GLFW_KEY_3, new IInputAction.HotbarActionI.Select(2)),
            Map.entry(GLFW_KEY_4, new IInputAction.HotbarActionI.Select(3)),
            Map.entry(GLFW_KEY_5, new IInputAction.HotbarActionI.Select(4)),
            Map.entry(GLFW_KEY_6, new IInputAction.HotbarActionI.Select(5)),
            Map.entry(GLFW_KEY_7, new IInputAction.HotbarActionI.Select(6)),
            Map.entry(GLFW_KEY_8, new IInputAction.HotbarActionI.Select(7)),
            Map.entry(GLFW_KEY_9, new IInputAction.HotbarActionI.Select(8)),
            Map.entry(GLFW_KEY_SPACE, IInputAction.Simple.JUMP),
            Map.entry(GLFW_KEY_I, IInputAction.Simple.INVENTORY),
            Map.entry(GLFW_KEY_F3, IInputAction.Simple.DEBUG_OVERLAY),
            Map.entry(GLFW_KEY_P, IInputAction.Simple.PAUSE),

            // mouse
            Map.entry(GLFW_MOUSE_BUTTON_1, IInputAction.Simple.BREAK_BLOCK),
            Map.entry(GLFW_MOUSE_BUTTON_2, IInputAction.Simple.PLACE_BLOCK));

    private final InputEventQueue queue;
    private final Map<Integer, IInputAction> bindings;
    private final Set<Integer> heldKeys = new HashSet<>();
    // Only WASD generate continuous MoveActions each frame. All other keys fire once on release.
    private final Set<Integer> holdableKeys = Set.of(GLFW_KEY_W, GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D);
    // NaN on startup so the first mouse event doesn't produce a huge delta from (0,0).
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public InputMapper(InputEventQueue queue) {
        this.queue = queue;
        this.bindings = DEFAULT_BINDINGS;
    }

    public InputMapper(InputEventQueue queue, Map<Integer, IInputAction> bindings, Set<Integer> heldKeys) {
        this.queue = queue;
        this.bindings = bindings;
        this.heldKeys.addAll(heldKeys);
    }

    public List<IInputAction> drain() {
        List<IInputAction> actions = new ArrayList<>();
        List<IRawInputEvent> IRawInputEvents = this.queue.drain();
        processRawInputEvents(IRawInputEvents, actions);
        processHeldActions(actions);
        return actions;
    }

    private void processRawInputEvents(List<IRawInputEvent> IRawInputEvents, List<IInputAction> actions) {
        for (var rawInputEvent : IRawInputEvents) {
            switch (rawInputEvent) {
                case IRawInputEvent.KeyEventI e -> {
                    if (e.action() == GLFW_RELEASE) {
                        // Fire the bound action on key-up, not key-down, to avoid double-firing
                        // with GLFW's built-in key-repeat events (GLFW_REPEAT is ignored here).
                        IInputAction action = this.bindings.get(e.key());
                        if (action != null) actions.add(action);
                        this.heldKeys.remove(e.key());
                    } else if (e.action() == GLFW_PRESS && this.holdableKeys.contains(e.key())) {
                        this.heldKeys.add(e.key());
                    }
                }
                case IRawInputEvent.MouseButtonEventI e -> {
                    if (e.action() == GLFW_RELEASE) {
                        IInputAction action = this.bindings.get(e.button());
                        if (action != null) actions.add(action);
                    }
                }
                case IRawInputEvent.MouseMoveEventI e -> {
                    if (!Double.isNaN(this.lastMouseX)) {
                        float dx = (float) (e.xpos() - this.lastMouseX);
                        float dy = (float) (e.ypos() - this.lastMouseY);
                        actions.add(new IInputAction.LookActionI(dx, dy));
                    }
                    this.lastMouseX = e.xpos();
                    this.lastMouseY = e.ypos();
                }
                case IRawInputEvent.ScrollEventI e -> actions.add(new IInputAction.ScrollActionI((float) e.yoffset()));
            }
        }
    }

    private void processHeldActions(List<IInputAction> actions) {
        float dx = 0f;
        float dz = 0f;
        if (this.heldKeys.contains(GLFW_KEY_W)) dz += 1f;
        if (this.heldKeys.contains(GLFW_KEY_S)) dz -= 1f;
        if (this.heldKeys.contains(GLFW_KEY_A)) dx -= 1f;
        if (this.heldKeys.contains(GLFW_KEY_D)) dx += 1f;
        if (dx != 0f || dz != 0f) actions.add(new IInputAction.MoveActionI(dx, dz));
    }
}
