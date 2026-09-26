package com.beneklund.minecraft.platform.input;

import static org.lwjgl.glfw.GLFW.*;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.platform.input.Binding.Trigger;
import java.util.*;

/**
 * Turns one frame of {@link IRawInputEvent}s into domain {@link IInputAction}s. The only class that
 * knows GLFW key codes, so rebinding a key touches the binding table here and nothing else.
 *
 * <p>Keys and mouse buttons share one code space and one path through their {@link Binding}. Tap
 * bindings fire on release. Hold bindings keep a per-code timer from press to release; {@code
 * GLFW_REPEAT} is ignored, so the repeat rate is the binding's, independent of the OS key-repeat
 * setting. Cursor positions become {@link IInputAction.LookAction} deltas, and the first position
 * after startup only seeds the previous position.
 *
 * @see <a href="https://www.glfw.org/docs/latest/input_guide.html#input_key">GLFW: Key input</a>
 */
public class InputMapper {

    private static final float CLICK_REPEAT_SECONDS = 0.25f;
    private static final float EVERY_FRAME = 0f;

    /**
     * WASD, space and shift hold every frame; mouse buttons hold at 0.25 s; everything else taps.
     */
    public static final Map<Integer, Binding> DEFAULT_BINDINGS = Map.ofEntries(
            // Movement: each key emits its own ±1 component every frame; Player sums them
            // (W+D -> forward + right -> normalized diagonal) so the input layer stays dumb.
            Map.entry(GLFW_KEY_W, Binding.hold(new IInputAction.MoveAction(0, 1), EVERY_FRAME)),
            Map.entry(GLFW_KEY_S, Binding.hold(new IInputAction.MoveAction(0, -1), EVERY_FRAME)),
            Map.entry(GLFW_KEY_A, Binding.hold(new IInputAction.MoveAction(-1, 0), EVERY_FRAME)),
            Map.entry(GLFW_KEY_D, Binding.hold(new IInputAction.MoveAction(1, 0), EVERY_FRAME)),
            Map.entry(GLFW_KEY_SPACE, Binding.hold(IInputAction.Simple.JUMP, EVERY_FRAME)),
            Map.entry(GLFW_KEY_LEFT_SHIFT, Binding.hold(IInputAction.Simple.SNEAK, EVERY_FRAME)),
            Map.entry(GLFW_MOUSE_BUTTON_1, Binding.hold(IInputAction.Simple.BREAK_BLOCK, CLICK_REPEAT_SECONDS)),
            Map.entry(GLFW_MOUSE_BUTTON_2, Binding.hold(IInputAction.Simple.PLACE_BLOCK, CLICK_REPEAT_SECONDS)),
            Map.entry(GLFW_KEY_ESCAPE, Binding.tap(IInputAction.Simple.EXIT)),
            Map.entry(GLFW_KEY_X, Binding.tap(IInputAction.Simple.EXIT)),
            Map.entry(GLFW_KEY_1, Binding.tap(new IInputAction.HotbarAction.Select(0))),
            Map.entry(GLFW_KEY_2, Binding.tap(new IInputAction.HotbarAction.Select(1))),
            Map.entry(GLFW_KEY_3, Binding.tap(new IInputAction.HotbarAction.Select(2))),
            Map.entry(GLFW_KEY_4, Binding.tap(new IInputAction.HotbarAction.Select(3))),
            Map.entry(GLFW_KEY_5, Binding.tap(new IInputAction.HotbarAction.Select(4))),
            Map.entry(GLFW_KEY_6, Binding.tap(new IInputAction.HotbarAction.Select(5))),
            Map.entry(GLFW_KEY_7, Binding.tap(new IInputAction.HotbarAction.Select(6))),
            Map.entry(GLFW_KEY_8, Binding.tap(new IInputAction.HotbarAction.Select(7))),
            Map.entry(GLFW_KEY_9, Binding.tap(new IInputAction.HotbarAction.Select(8))),
            Map.entry(GLFW_KEY_I, Binding.tap(IInputAction.Simple.INVENTORY)),
            Map.entry(GLFW_KEY_F2, Binding.tap(IInputAction.Simple.SCREENSHOT)),
            Map.entry(GLFW_KEY_F3, Binding.tap(IInputAction.Simple.DEBUG_OVERLAY)),
            Map.entry(GLFW_KEY_F5, Binding.tap(IInputAction.Simple.RELOAD_SHADERS)),
            Map.entry(GLFW_KEY_F6, Binding.tap(IInputAction.Simple.DEBUG_SHADOW_MAP)),
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

    /**
     * Drains the event queue and returns this frame's actions: event-driven ones in arrival order,
     * then any held bindings whose repeat timer came due.
     *
     * @param dt frame time in seconds, advancing the hold timers
     */
    public List<IInputAction> drain(float dt) {
        List<IInputAction> actions = new ArrayList<>();
        for (IRawInputEvent event : queue.drain()) {
            switch (event) {
                case IRawInputEvent.KeyEvent e -> handleButton(e.key(), e.action(), actions);
                case IRawInputEvent.MouseButtonEvent e -> handleButton(e.button(), e.action(), actions);
                case IRawInputEvent.MouseMoveEvent e -> handleMouseMove(e, actions);
                case IRawInputEvent.ScrollEvent e -> actions.add(new IInputAction.ScrollAction((float) e.yoffset()));
            }
        }
        processHeld(dt, actions);
        return actions;
    }

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
            if (elapsed >= repeatSeconds) {
                actions.add(binding.action());
                elapsed = 0f;
            }
            entry.setValue(elapsed);
        }
    }

    private void handleMouseMove(IRawInputEvent.MouseMoveEvent e, List<IInputAction> actions) {
        if (!Double.isNaN(lastMouseX)) {
            float dx = (float) (e.xpos() - lastMouseX);
            float dy = (float) (e.ypos() - lastMouseY);
            actions.add(new IInputAction.LookAction(dx, dy));
        }
        lastMouseX = e.xpos();
        lastMouseY = e.ypos();
    }
}
