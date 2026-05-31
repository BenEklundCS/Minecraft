package com.beneklund.minecraft.input;

// Domain-level input intent. InputMapper translates raw GLFW events into these types
// so game logic never touches GLFW key codes directly.
public sealed interface InputAction {
    // dx/dz are un-normalized direction components; InputMapper sets them to ±1.
    record MoveAction(float dx, float dz) implements InputAction {}

    // Raw pixel delta from the previous cursor position, before sensitivity scaling.
    record LookAction(float dx, float dy) implements InputAction {}

    record ScrollAction(float delta) implements InputAction {}

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
        NONE,
        SLOT_1,
        SLOT_2,
        SLOT_3,
        SLOT_4,
        SLOT_5,
        SLOT_6,
        SLOT_7,
        SLOT_8,
        SLOT_9
    }
}
