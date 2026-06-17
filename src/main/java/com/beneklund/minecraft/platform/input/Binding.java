package com.beneklund.minecraft.platform.input;

import com.beneklund.minecraft.input.IInputAction;

// Ties a raw input code (key or mouse button) to the action it produces and how it triggers.
// Keeping the trigger here is what lets bindings be fully data-driven — no side list of
// "which keys are holdable" to keep in sync.
public record Binding(IInputAction action, Trigger trigger) {

    public sealed interface Trigger {
        // Fires once, on key-up. Firing on release (not press) sidesteps GLFW's auto-repeat,
        // which streams PRESS then repeated REPEAT events while a key is held.
        record Tap() implements Trigger {}

        // Fires while held: immediately on press, then once every repeatSeconds.
        // repeatSeconds == 0 means "every frame" (movement, jump); > 0 rate-limits it
        // (holding to mine/place repeatedly instead of once-per-frame spam).
        record Hold(float repeatSeconds) implements Trigger {}
    }

    public static Binding tap(IInputAction action) {
        return new Binding(action, new Trigger.Tap());
    }

    public static Binding hold(IInputAction action, float repeatSeconds) {
        return new Binding(action, new Trigger.Hold(repeatSeconds));
    }
}
