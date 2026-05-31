package com.beneklund.minecraft.platform.input;

import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

// GLFW callback data captured verbatim and queued for the game thread to process.
// The callback static factories are here so the wiring stays self-contained per event type.
public sealed interface RawInputEvent {
    record KeyEvent(int key, int scancode, int action, int mods) implements RawInputEvent {
        public static GLFWKeyCallbackI callback(InputEventQueue queue) {
            return ((window, key, scancode, action, mods) -> queue.offer(new KeyEvent(key, scancode, action, mods)));
        }
    }

    record MouseMoveEvent(double xpos, double ypos) implements RawInputEvent {
        public static GLFWCursorPosCallbackI callback(InputEventQueue queue) {
            return (window, xpos, ypos) -> queue.offer(new MouseMoveEvent(xpos, ypos));
        }
    }

    record ScrollEvent(double xoffset, double yoffset) implements RawInputEvent {
        public static GLFWScrollCallbackI callback(InputEventQueue queue) {
            return (window, xoffset, yoffset) -> queue.offer(new ScrollEvent(xoffset, yoffset));
        }
    }

    record MouseButtonEvent(int button, int action, int mods) implements RawInputEvent {
        public static GLFWMouseButtonCallbackI callback(InputEventQueue queue) {
            return (window, button, action, mods) -> queue.offer(new MouseButtonEvent(button, action, mods));
        }
    }
}
