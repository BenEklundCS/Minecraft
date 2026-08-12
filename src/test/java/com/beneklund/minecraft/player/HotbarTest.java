package com.beneklund.minecraft.player;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import org.junit.jupiter.api.Test;

// Zero-indexing is the whole reason this class exists — four call sites in Player used to add 1
// to reach a map keyed 1-9. Every assertion here is written in slot numbers the caller would use.
class HotbarTest {

    @Test
    void newHotbar_startsOnTheFirstSlot() {
        Hotbar hotbar = new Hotbar();

        assertEquals(0, hotbar.selected());
        assertEquals(Block.STONE, hotbar.blockAt(0), "slot 0 is key '1', not key '0'");
    }

    @Test
    void select_movesTheSelection() {
        Hotbar hotbar = new Hotbar();

        hotbar.select(4);

        assertEquals(4, hotbar.selected());
        assertEquals(Block.SAND, hotbar.blockAt(hotbar.selected()));
    }

    // The last slot is 8, not 9. Passing 9 is the off-by-one this class was extracted to prevent.
    @Test
    void select_pastTheLastSlot_throws() {
        Hotbar hotbar = new Hotbar();

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> hotbar.select(Hotbar.SLOT_COUNT));

        assertTrue(e.getMessage().contains("9"), "message names the offending slot");
        assertEquals(0, hotbar.selected(), "a rejected select leaves the selection alone");
    }

    @Test
    void select_belowZero_throws() {
        Hotbar hotbar = new Hotbar();

        assertThrows(IllegalArgumentException.class, () -> hotbar.select(-1));
    }

    @Test
    void scroll_forward_advancesOneSlot() {
        Hotbar hotbar = new Hotbar();

        hotbar.scroll(1);

        assertEquals(1, hotbar.selected());
    }

    // Scrolling up off the end comes back to the start rather than running past the array.
    @Test
    void scroll_forwardOffTheEnd_wrapsToTheStart() {
        Hotbar hotbar = new Hotbar();
        hotbar.select(Hotbar.SLOT_COUNT - 1);

        hotbar.scroll(1);

        assertEquals(0, hotbar.selected());
    }

    // The direction floorMod exists for — plain % would give -1 here.
    @Test
    void scroll_backwardOffTheStart_wrapsToTheEnd() {
        Hotbar hotbar = new Hotbar();

        hotbar.scroll(-1);

        assertEquals(Hotbar.SLOT_COUNT - 1, hotbar.selected());
        assertEquals(Block.OAK_LEAF, hotbar.blockAt(hotbar.selected()));
    }

    @Test
    void scroll_aFullLap_returnsToWhereItStarted() {
        Hotbar hotbar = new Hotbar();
        hotbar.select(3);

        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) hotbar.scroll(1);

        assertEquals(3, hotbar.selected());
    }

    @Test
    void snapshot_hasEverySlotInOrder() {
        Hotbar hotbar = new Hotbar();

        Block[] slots = hotbar.snapshot();

        assertEquals(Hotbar.SLOT_COUNT, slots.length);
        for (int i = 0; i < slots.length; i++) assertEquals(hotbar.blockAt(i), slots[i], "slot " + i);
    }

    // The HUD keeps the previous snapshot and diffs against it to decide whether to rebuild its
    // mesh. Handing back the live array would make every frame compare equal and never redraw.
    @Test
    void snapshot_isACopy() {
        Hotbar hotbar = new Hotbar();

        Block[] slots = hotbar.snapshot();
        slots[0] = Block.BEDROCK;

        assertEquals(Block.STONE, hotbar.blockAt(0), "mutating a snapshot must not reach the hotbar");
    }

    @Test
    void blockAt_outOfRange_throws() {
        Hotbar hotbar = new Hotbar();

        assertThrows(IllegalArgumentException.class, () -> hotbar.blockAt(Hotbar.SLOT_COUNT));
        assertThrows(IllegalArgumentException.class, () -> hotbar.blockAt(-1));
    }
}
