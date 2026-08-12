package com.beneklund.minecraft.player;

import com.beneklund.minecraft.block.Block;

// The nine hotbar slots and which one is selected. Zero-indexed the whole way through:
// slot 0 is the key '1', matching HotbarAction.Select, so no caller ever adds 1 to anything.
// A null slot is an empty one — the HUD already draws those as bare frames.
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
        Block.OAK_LEAF
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

    // Fresh array each call — the HUD holds onto what it gets and compares against it next
    // frame, so handing out the live one would make every frame look unchanged.
    public Block[] snapshot() {
        return slots.clone();
    }

    public void select(int slot) {
        checkSlot(slot);
        selected = slot;
    }

    // Wraps in both directions: scrolling off either end lands on the other. floorMod rather
    // than % so a negative step wraps instead of going negative.
    public void scroll(int step) {
        selected = Math.floorMod(selected + step, SLOT_COUNT);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT)
            throw new IllegalArgumentException("slot %d out of range 0..%d".formatted(slot, SLOT_COUNT - 1));
    }
}
