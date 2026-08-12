package com.beneklund.minecraft.platform.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.input.IInputAction.Simple;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InputMapperTest {

    private static final float FRAME = 1f / 60f;

    private static InputMapper mapper(InputEventQueue queue) {
        return new InputMapper(queue, InputMapper.DEFAULT_BINDINGS);
    }

    // --- Movement: held bindings fire while down, starting on press ---

    record WasdCase(int key, float expectedDx, float expectedDz) {}

    static List<WasdCase> wasdCases() {
        return List.of(
                new WasdCase(GLFW_KEY_W, 0f, 1f),
                new WasdCase(GLFW_KEY_S, 0f, -1f),
                new WasdCase(GLFW_KEY_A, -1f, 0f),
                new WasdCase(GLFW_KEY_D, 1f, 0f));
    }

    @ParameterizedTest
    @MethodSource("wasdCases")
    void keyHeld_emitsMoveActionEveryFrame(WasdCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.KeyEvent(tc.key(), 0, GLFW_PRESS, 0));

        List<IInputAction> actions = mapper.drain(FRAME);

        assertEquals(1, actions.size());
        assertEquals(new IInputAction.MoveAction(tc.expectedDx(), tc.expectedDz()), actions.getFirst());
    }

    @Test
    void spaceHeld_emitsJumpOnPress() {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.KeyEvent(GLFW_KEY_SPACE, 0, GLFW_PRESS, 0));

        assertEquals(List.of(Simple.JUMP), mapper.drain(FRAME));
    }

    @Test
    void heldKey_stopsEmittingAfterRelease() {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);

        queue.offer(new IRawInputEvent.KeyEvent(GLFW_KEY_W, 0, GLFW_PRESS, 0));
        assertEquals(1, mapper.drain(FRAME).size()); // fires while held

        queue.offer(new IRawInputEvent.KeyEvent(GLFW_KEY_W, 0, GLFW_RELEASE, 0));
        assertTrue(mapper.drain(FRAME).isEmpty()); // nothing once released
    }

    // --- Taps: fire once, on release ---

    record TapKeyCase(int key, Simple expected) {}

    static List<TapKeyCase> tapKeyCases() {
        return List.of(
                new TapKeyCase(GLFW_KEY_ESCAPE, Simple.EXIT),
                new TapKeyCase(GLFW_KEY_X, Simple.EXIT),
                new TapKeyCase(GLFW_KEY_I, Simple.INVENTORY),
                new TapKeyCase(GLFW_KEY_F3, Simple.DEBUG_OVERLAY),
                new TapKeyCase(GLFW_KEY_P, Simple.PAUSE));
    }

    @ParameterizedTest
    @MethodSource("tapKeyCases")
    void keyRelease_returnsTapAction(TapKeyCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.KeyEvent(tc.key(), 0, GLFW_RELEASE, 0));

        assertEquals(List.of(tc.expected()), mapper.drain(FRAME));
    }

    @Test
    void tapKeyPress_emitsNothing() {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.KeyEvent(GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0));

        assertTrue(mapper.drain(FRAME).isEmpty());
    }

    // --- Hotbar select (tap, on release) ---

    record HotbarCase(int key, int expectedSlot) {}

    static List<HotbarCase> hotbarCases() {
        return List.of(
                new HotbarCase(GLFW_KEY_1, 0),
                new HotbarCase(GLFW_KEY_2, 1),
                new HotbarCase(GLFW_KEY_3, 2),
                new HotbarCase(GLFW_KEY_4, 3),
                new HotbarCase(GLFW_KEY_5, 4),
                new HotbarCase(GLFW_KEY_6, 5),
                new HotbarCase(GLFW_KEY_7, 6),
                new HotbarCase(GLFW_KEY_8, 7),
                new HotbarCase(GLFW_KEY_9, 8));
    }

    @ParameterizedTest
    @MethodSource("hotbarCases")
    void keyRelease_returnsHotbarSelect(HotbarCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.KeyEvent(tc.key(), 0, GLFW_RELEASE, 0));

        assertEquals(List.of(new IInputAction.HotbarAction.Select(tc.expectedSlot())), mapper.drain(FRAME));
    }

    // --- Mouse buttons: now held, fire on press, repeat on a delay ---

    record MouseCase(int button, Simple expected) {}

    static List<MouseCase> mouseCases() {
        return List.of(
                new MouseCase(GLFW_MOUSE_BUTTON_1, Simple.BREAK_BLOCK),
                new MouseCase(GLFW_MOUSE_BUTTON_2, Simple.PLACE_BLOCK));
    }

    @ParameterizedTest
    @MethodSource("mouseCases")
    void mouseButtonPress_firesActionImmediately(MouseCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.MouseButtonEvent(tc.button(), GLFW_PRESS, 0));

        assertEquals(List.of(tc.expected()), mapper.drain(FRAME));
    }

    @Test
    void heldMouseButton_repeatsOnlyAfterDelay() {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);

        // press: first hit lands immediately
        queue.offer(new IRawInputEvent.MouseButtonEvent(GLFW_MOUSE_BUTTON_1, GLFW_PRESS, 0));
        assertTrue(mapper.drain(0.01f).contains(Simple.BREAK_BLOCK), "first hit on press");

        // not enough time has passed for a repeat
        assertFalse(mapper.drain(0.1f).contains(Simple.BREAK_BLOCK), "no repeat before the delay");

        // crossing the 0.25s cadence fires the next hit
        assertTrue(mapper.drain(0.2f).contains(Simple.BREAK_BLOCK), "repeats after the delay elapses");
    }

    // --- Scroll passes through as a ScrollAction ---

    @Test
    void scroll_emitsScrollAction() {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = mapper(queue);
        queue.offer(new IRawInputEvent.ScrollEvent(0, 1.0));

        assertEquals(List.of(new IInputAction.ScrollAction(1.0f)), mapper.drain(FRAME));
    }
}
