package com.beneklund.minecraft.input;

// Domain-level input intent. InputMapper translates raw GLFW events into these types
// so game logic never touches GLFW key codes directly.
public sealed interface InputAction {
    // dx/dz are un-normalized direction components; InputMapper sets them to ±1.
    record MoveAction(float dx, float dz) implements InputAction {}

    // Raw pixel delta from the previous cursor position, before sensitivity scaling.
    record LookAction(float dx, float dy) implements InputAction {}

    record ScrollAction(float delta) implements InputAction {}

    // slot is 0-indexed: slot 0 = hotbar key '1', slot 8 = hotbar key '9'.
    sealed interface HotbarAction extends InputAction {
        record Select(int slot) implements HotbarAction {}
    }

    enum Simple implements InputAction {
        JUMP,
        BREAK_BLOCK,
        PLACE_BLOCK,
        SLOT_NEXT,
        SLOT_PREV,
        PAUSE,
        DEBUG_OVERLAY,
        INVENTORY,
        EXIT,
        NONE
    }
}
