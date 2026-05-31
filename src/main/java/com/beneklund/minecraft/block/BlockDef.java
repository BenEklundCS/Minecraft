package com.beneklund.minecraft.block;

import com.beneklund.minecraft.util.Direction;

// tileNames is indexed by Direction.ordinal() — the ordering must stay in sync with the Direction enum.
public record BlockDef(boolean solid, boolean transparent, String[] tileNames) {
    // Helper to enforce face direction on loaded blocks.
    public static BlockDef build(
            boolean solid,
            boolean transparent,
            String up,
            String down,
            String north,
            String south,
            String east,
            String west) {
        return new BlockDef(solid, transparent, new String[] {up, down, north, south, east, west});
    }

    public String getTileFace(Direction direction) {
        return this.tileNames[direction.ordinal()];
    }
}
