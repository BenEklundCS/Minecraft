package com.beneklund.minecraft.input;

// Domain-level input intent. InputMapper translates raw GLFW events into these types
// so game logic never touches GLFW key codes directly.
public sealed interface IInputAction {
    // dx/dz are un-normalized direction components; InputMapper sets them to ±1.
    record MoveAction(float dx, float dz) implements IInputAction {}

    // Raw pixel delta from the previous cursor position, before sensitivity scaling.
    record LookAction(float dx, float dy) implements IInputAction {}

    record ScrollAction(float delta) implements IInputAction {}

    // slot is 0-indexed: slot 0 = hotbar key '1', slot 8 = hotbar key '9'.
    sealed interface HotbarAction extends IInputAction {
        record Select(int slot) implements HotbarAction {}
    }

    enum Simple implements IInputAction {
        JUMP,
        SNEAK,
        BREAK_BLOCK,
        PLACE_BLOCK,
        SLOT_NEXT,
        SLOT_PREV,
        PAUSE,
        DEBUG_OVERLAY,
        INVENTORY,
        RELOAD_SHADERS,
        SCREENSHOT,
        EXIT,
        NONE,
    }
}
