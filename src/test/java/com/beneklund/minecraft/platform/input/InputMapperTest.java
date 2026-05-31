package com.beneklund.minecraft.platform.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.*;

import com.beneklund.minecraft.input.InputAction;
import com.beneklund.minecraft.input.InputAction.Simple;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InputMapperTest {

    // --- WASD move actions ---

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
    void keyPress_returnsMoveActionWithCorrectDxDz(WasdCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue, InputMapper.DEFAULT_BINDINGS, new HashSet<>());
        queue.offer(new RawInputEvent.KeyEvent(tc.key(), 0, GLFW_PRESS, 0));

        List<InputAction> actions = mapper.drain();

        assertEquals(1, actions.size());
        assertEquals(new InputAction.MoveAction(tc.expectedDx(), tc.expectedDz()), actions.getFirst());
    }

    // --- Simple key actions (fire on release) ---

    record SimpleKeyCase(int key, Simple expected) {}

    static List<SimpleKeyCase> simpleKeyCases() {
        return List.of(
                new SimpleKeyCase(GLFW_KEY_ESCAPE, Simple.EXIT),
                new SimpleKeyCase(GLFW_KEY_X, Simple.EXIT),
                new SimpleKeyCase(GLFW_KEY_SPACE, Simple.JUMP),
                new SimpleKeyCase(GLFW_KEY_I, Simple.INVENTORY),
                new SimpleKeyCase(GLFW_KEY_F3, Simple.DEBUG_OVERLAY),
                new SimpleKeyCase(GLFW_KEY_P, Simple.PAUSE));
    }

    // --- Hotbar select actions ---

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
        InputMapper mapper = new InputMapper(queue, InputMapper.DEFAULT_BINDINGS, new HashSet<>());
        queue.offer(new RawInputEvent.KeyEvent(tc.key(), 0, GLFW_RELEASE, 0));

        List<InputAction> actions = mapper.drain();

        assertEquals(1, actions.size());
        assertEquals(new InputAction.HotbarAction.Select(tc.expectedSlot()), actions.getFirst());
    }

    @ParameterizedTest
    @MethodSource("simpleKeyCases")
    void keyRelease_returnsSimpleAction(SimpleKeyCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue, InputMapper.DEFAULT_BINDINGS, new HashSet<>());
        queue.offer(new RawInputEvent.KeyEvent(tc.key(), 0, GLFW_RELEASE, 0));

        List<InputAction> actions = mapper.drain();

        assertEquals(1, actions.size());
        assertEquals(tc.expected(), actions.getFirst());
    }

    // --- Simple mouse button actions (fire on release) ---

    record SimpleMouseCase(int button, Simple expected) {}

    static List<SimpleMouseCase> simpleMouseCases() {
        return List.of(
                new SimpleMouseCase(GLFW_MOUSE_BUTTON_1, Simple.BREAK_BLOCK),
                new SimpleMouseCase(GLFW_MOUSE_BUTTON_2, Simple.PLACE_BLOCK));
    }

    @ParameterizedTest
    @MethodSource("simpleMouseCases")
    void mouseButtonRelease_returnsSimpleAction(SimpleMouseCase tc) {
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue, InputMapper.DEFAULT_BINDINGS, new HashSet<>());
        queue.offer(new RawInputEvent.MouseButtonEvent(tc.button(), GLFW_RELEASE, 0));

        List<InputAction> actions = mapper.drain();

        assertEquals(1, actions.size());
        assertEquals(tc.expected(), actions.getFirst());
    }
}
