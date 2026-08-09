package com.beneklund.minecraft.platform.input;

import static org.lwjgl.glfw.GLFW.*;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.input.Binding.Trigger;
import java.util.*;

// Maps raw GLFW events to domain InputActions. Every code's behavior (tap vs hold, and
// how fast a hold repeats) lives in its Binding, so there's no separate "holdable keys" set.
public class InputMapper {

    // Default repeat cadence for mining/placing while the mouse is held down.
    private static final float CLICK_REPEAT_SECONDS = 0.25f;
    // Movement and jump repeat every frame while held.
    private static final float EVERY_FRAME = 0f;

    public static final Map<Integer, Binding> DEFAULT_BINDINGS = Map.ofEntries(
            // Movement: each key emits its own ±1 component every frame; Player sums them
            // (W+D -> forward + right -> normalized diagonal) so the input layer stays dumb.
            Map.entry(GLFW_KEY_W, Binding.hold(new IInputAction.MoveActionI(0, 1), EVERY_FRAME)),
            Map.entry(GLFW_KEY_S, Binding.hold(new IInputAction.MoveActionI(0, -1), EVERY_FRAME)),
            Map.entry(GLFW_KEY_A, Binding.hold(new IInputAction.MoveActionI(-1, 0), EVERY_FRAME)),
            Map.entry(GLFW_KEY_D, Binding.hold(new IInputAction.MoveActionI(1, 0), EVERY_FRAME)),
            Map.entry(GLFW_KEY_SPACE, Binding.hold(IInputAction.Simple.JUMP, EVERY_FRAME)),
            Map.entry(GLFW_KEY_LEFT_SHIFT, Binding.hold(IInputAction.Simple.SNEAK, EVERY_FRAME)),

            // Mouse: held with a cadence so holding the button mines/places on a timer.
            Map.entry(GLFW_MOUSE_BUTTON_1, Binding.hold(IInputAction.Simple.BREAK_BLOCK, CLICK_REPEAT_SECONDS)),
            Map.entry(GLFW_MOUSE_BUTTON_2, Binding.hold(IInputAction.Simple.PLACE_BLOCK, CLICK_REPEAT_SECONDS)),

            // Taps: fire once on release.
            Map.entry(GLFW_KEY_ESCAPE, Binding.tap(IInputAction.Simple.EXIT)),
            Map.entry(GLFW_KEY_X, Binding.tap(IInputAction.Simple.EXIT)),
            Map.entry(GLFW_KEY_1, Binding.tap(new IInputAction.HotbarActionI.Select(0))),
            Map.entry(GLFW_KEY_2, Binding.tap(new IInputAction.HotbarActionI.Select(1))),
            Map.entry(GLFW_KEY_3, Binding.tap(new IInputAction.HotbarActionI.Select(2))),
            Map.entry(GLFW_KEY_4, Binding.tap(new IInputAction.HotbarActionI.Select(3))),
            Map.entry(GLFW_KEY_5, Binding.tap(new IInputAction.HotbarActionI.Select(4))),
            Map.entry(GLFW_KEY_6, Binding.tap(new IInputAction.HotbarActionI.Select(5))),
            Map.entry(GLFW_KEY_7, Binding.tap(new IInputAction.HotbarActionI.Select(6))),
            Map.entry(GLFW_KEY_8, Binding.tap(new IInputAction.HotbarActionI.Select(7))),
            Map.entry(GLFW_KEY_9, Binding.tap(new IInputAction.HotbarActionI.Select(8))),
            Map.entry(GLFW_KEY_I, Binding.tap(IInputAction.Simple.INVENTORY)),
            Map.entry(GLFW_KEY_F3, Binding.tap(IInputAction.Simple.DEBUG_OVERLAY)),
            Map.entry(GLFW_KEY_F5, Binding.tap(IInputAction.Simple.RELOAD_SHADERS)),
            Map.entry(GLFW_KEY_P, Binding.tap(IInputAction.Simple.PAUSE)));

    private final InputEventQueue queue;
    private final Map<Integer, Binding> bindings;
    // Codes currently held that have a Hold trigger, mapped to seconds accumulated since their
    // last emit. Presence in this map == currently held; absence == up.
    private final Map<Integer, Float> heldTimers = new HashMap<>();
    // NaN on startup so the first mouse event doesn't produce a huge delta from (0,0).
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public InputMapper(InputEventQueue queue) {
        this(queue, DEFAULT_BINDINGS);
    }

    public InputMapper(InputEventQueue queue, Map<Integer, Binding> bindings) {
        this.queue = queue;
        this.bindings = bindings;
    }

    // dt is the frame time in seconds; it advances the repeat timers for held bindings.
    public List<IInputAction> drain(float dt) {
        List<IInputAction> actions = new ArrayList<>();
        for (IRawInputEvent event : queue.drain()) {
            switch (event) {
                case IRawInputEvent.KeyEventI e -> handleButton(e.key(), e.action(), actions);
                case IRawInputEvent.MouseButtonEventI e -> handleButton(e.button(), e.action(), actions);
                case IRawInputEvent.MouseMoveEventI e -> handleMouseMove(e, actions);
                case IRawInputEvent.ScrollEventI e -> actions.add(new IInputAction.ScrollActionI((float) e.yoffset()));
            }
        }
        processHeld(dt, actions);
        return actions;
    }

    // Keys and mouse buttons share this path — both are just integer codes with a Binding.
    private void handleButton(int code, int glfwAction, List<IInputAction> actions) {
        Binding binding = bindings.get(code);
        if (binding == null) return;
        switch (binding.trigger()) {
            case Trigger.Tap tap -> {
                if (glfwAction == GLFW_RELEASE) actions.add(binding.action());
            }
            case Trigger.Hold hold -> {
                if (glfwAction == GLFW_PRESS) {
                    // Prime the timer at the repeat interval so processHeld fires it instantly
                    // this frame (no wait for the first hit), then spaces out subsequent ones.
                    heldTimers.put(code, hold.repeatSeconds());
                } else if (glfwAction == GLFW_RELEASE) {
                    heldTimers.remove(code);
                }
                // GLFW_REPEAT is ignored — our own timer drives repetition.
            }
        }
    }

    private void processHeld(float dt, List<IInputAction> actions) {
        for (var entry : heldTimers.entrySet()) {
            Binding binding = bindings.get(entry.getKey());
            float repeatSeconds = ((Trigger.Hold) binding.trigger()).repeatSeconds();
            float elapsed = entry.getValue() + dt;
            if (elapsed >= repeatSeconds) { // repeat 0 -> true every frame
                actions.add(binding.action());
                elapsed = 0f;
            }
            entry.setValue(elapsed);
        }
    }

    private void handleMouseMove(IRawInputEvent.MouseMoveEventI e, List<IInputAction> actions) {
        if (!Double.isNaN(lastMouseX)) {
            float dx = (float) (e.xpos() - lastMouseX);
            float dy = (float) (e.ypos() - lastMouseY);
            actions.add(new IInputAction.LookActionI(dx, dy));
        }
        lastMouseX = e.xpos();
        lastMouseY = e.ypos();
    }
}
