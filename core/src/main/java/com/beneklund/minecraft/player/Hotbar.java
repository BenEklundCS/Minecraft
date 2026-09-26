package com.beneklund.minecraft.player;

import com.beneklund.minecraft.block.Block;

/**
 * The nine hotbar slots and the selected one.
 *
 * <p>Slots are zero-indexed everywhere: slot 0 is the key {@code 1}, matching {@code
 * HotbarAction.Select}, so no caller adds or subtracts 1. A {@code null} slot is empty, which the
 * HUD draws as a bare frame.
 */
public class Hotbar {
    public static final int SLOT_COUNT = 9;

    private static final Block[] DEFAULT_PALETTE = {
        Block.STONE,
        Block.DIRT,
        Block.GRASS,
        Block.BEDROCK,
        Block.SAND,
        Block.GRAVEL,
        Block.OAK_LOG,
        Block.OAK_PLANK,
        Block.GLOWSTONE
    };

    private final Block[] slots;
    private int selected = 0;

    public Hotbar() {
        slots = DEFAULT_PALETTE.clone();
    }

    public int selected() {
        return selected;
    }

    public Block blockAt(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    /**
     * A copy of the slots. The HUD keeps the array it gets and compares against it next frame, so
     * handing out the live array would make every frame look unchanged.
     */
    public Block[] snapshot() {
        return slots.clone();
    }

    public void select(int slot) {
        checkSlot(slot);
        selected = slot;
    }

    /**
     * Moves the selection by {@code step}, wrapping at both ends. {@code Math.floorMod} keeps a
     * negative step in range where {@code %} would go negative.
     */
    public void scroll(int step) {
        selected = Math.floorMod(selected + step, SLOT_COUNT);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT)
            throw new IllegalArgumentException("slot %d out of range 0..%d".formatted(slot, SLOT_COUNT - 1));
    }
}
