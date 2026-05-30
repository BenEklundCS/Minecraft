package com.beneklund.minecraft.input;

public sealed interface InputAction {
    record MoveAction(float dx, float dz) implements InputAction {}

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
