package com.beneklund.minecraft.platform.input;

import com.beneklund.minecraft.input.IInputAction;

/**
 * The action a key or mouse button produces, and when it fires.
 *
 * <p>The trigger travels with the action, so a binding table is the complete description of input
 * behaviour, with no separate list of holdable keys to keep in sync.
 */
public record Binding(IInputAction action, Trigger trigger) {

    public sealed interface Trigger {
        /**
         * Fires once, on release. Release happens once per press, while GLFW streams {@code
         * GLFW_REPEAT} events for as long as a key is held.
         */
        record Tap() implements Trigger {}

        /**
         * Fires on the frame of the press, then every {@code repeatSeconds} while held. Zero fires
         * every frame, for movement and jump; a positive value rate-limits, so holding the mouse
         * mines on a cadence.
         */
        record Hold(float repeatSeconds) implements Trigger {}
    }

    public static Binding tap(IInputAction action) {
        return new Binding(action, new Trigger.Tap());
    }

    public static Binding hold(IInputAction action, float repeatSeconds) {
        return new Binding(action, new Trigger.Hold(repeatSeconds));
    }
}
