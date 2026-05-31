package com.beneklund.minecraft.platform.input;

import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

// GLFW callback data captured verbatim and queued for the game thread to process.
// The callback static factories are here so the wiring stays self-contained per event type.
public sealed interface IRawInputEvent {
    record KeyEventI(int key, int scancode, int action, int mods) implements IRawInputEvent {
        public static GLFWKeyCallbackI callback(InputEventQueue queue) {
            return ((window, key, scancode, action, mods) -> queue.offer(new KeyEventI(key, scancode, action, mods)));
        }
    }

    record MouseMoveEventI(double xpos, double ypos) implements IRawInputEvent {
        public static GLFWCursorPosCallbackI callback(InputEventQueue queue) {
            return (window, xpos, ypos) -> queue.offer(new MouseMoveEventI(xpos, ypos));
        }
    }

    record ScrollEventI(double xoffset, double yoffset) implements IRawInputEvent {
        public static GLFWScrollCallbackI callback(InputEventQueue queue) {
            return (window, xoffset, yoffset) -> queue.offer(new ScrollEventI(xoffset, yoffset));
        }
    }

    record MouseButtonEventI(int button, int action, int mods) implements IRawInputEvent {
        public static GLFWMouseButtonCallbackI callback(InputEventQueue queue) {
            return (window, button, action, mods) -> queue.offer(new MouseButtonEventI(button, action, mods));
        }
    }
}
