package com.beneklund.minecraft.input;

/**
 * What the player intends, independent of which key or button produced it. {@code InputMapper}
 * translates GLFW events into these, and game logic switches over them exhaustively.
 */
public sealed interface IInputAction {
    /** One movement key's contribution; {@code InputMapper} emits one per held key, each ±1. */
    record MoveAction(float dx, float dz) implements IInputAction {}

    /** Cursor delta in pixels since the previous position, before sensitivity scaling. */
    record LookAction(float dx, float dy) implements IInputAction {}

    /** Vertical scroll offset; positive is wheel up. */
    record ScrollAction(float delta) implements IInputAction {}

    sealed interface HotbarAction extends IInputAction {
        /** Selects a hotbar slot, 0-indexed: key 1 is slot 0, key 9 is slot 8. */
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
        DEBUG_SHADOW_MAP,
        SCREENSHOT,
        EXIT,
        NONE,
    }
}
