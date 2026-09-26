package com.beneklund.minecraft.platform.input;

import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

/**
 * GLFW callback arguments captured verbatim, minus the window handle. Codes and actions are
 * GLFW's ({@code GLFW_KEY_*}, {@code GLFW_PRESS}); {@link InputMapper} is the only reader, so the
 * GLFW vocabulary stays inside {@code platform/input}.
 *
 * <p>Each record's static {@code callback} builds the GLFW callback that enqueues it, which keeps
 * each event type's wiring in one place.
 *
 * @see <a href="https://www.glfw.org/docs/latest/input_guide.html">GLFW: Input guide</a>
 */
public sealed interface IRawInputEvent {
    record KeyEvent(int key, int scancode, int action, int mods) implements IRawInputEvent {
        public static GLFWKeyCallbackI callback(InputEventQueue queue) {
            return ((window, key, scancode, action, mods) -> queue.offer(new KeyEvent(key, scancode, action, mods)));
        }
    }

    record MouseMoveEvent(double xpos, double ypos) implements IRawInputEvent {
        public static GLFWCursorPosCallbackI callback(InputEventQueue queue) {
            return (window, xpos, ypos) -> queue.offer(new MouseMoveEvent(xpos, ypos));
        }
    }

    record ScrollEvent(double xoffset, double yoffset) implements IRawInputEvent {
        public static GLFWScrollCallbackI callback(InputEventQueue queue) {
            return (window, xoffset, yoffset) -> queue.offer(new ScrollEvent(xoffset, yoffset));
        }
    }

    record MouseButtonEvent(int button, int action, int mods) implements IRawInputEvent {
        public static GLFWMouseButtonCallbackI callback(InputEventQueue queue) {
            return (window, button, action, mods) -> queue.offer(new MouseButtonEvent(button, action, mods));
        }
    }
}
