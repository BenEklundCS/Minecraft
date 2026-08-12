package com.beneklund.minecraft.block;

import com.beneklund.minecraft.util.Direction;

// tileNames is indexed by Direction.ordinal() — the ordering must stay in sync with the Direction enum.
public record BlockDef(boolean solid, boolean transparent, boolean blended, boolean breakable, String[] tileNames) {
    public BlockDef(boolean solid, boolean transparent, boolean breakable, String[] tileNames) {
        this(solid, transparent, false, breakable, tileNames);
    }

    public BlockDef withBlending() {
        return new BlockDef(solid, transparent, true, breakable, tileNames);
    }

    // Explicit ordinal mapping so Direction reordering can't silently corrupt tile lookups.
    public static BlockDef build(
            boolean solid,
            boolean transparent,
            boolean breakable,
            String up,
            String down,
            String north,
            String south,
            String east,
            String west) {
        String[] tileNames = new String[Direction.values().length];
        tileNames[Direction.UP.ordinal()] = up;
        tileNames[Direction.DOWN.ordinal()] = down;
        tileNames[Direction.NORTH.ordinal()] = north;
        tileNames[Direction.SOUTH.ordinal()] = south;
        tileNames[Direction.EAST.ordinal()] = east;
        tileNames[Direction.WEST.ordinal()] = west;
        return new BlockDef(solid, transparent, breakable, tileNames);
    }

    public String getTileFace(Direction direction) {
        return tileNames[direction.ordinal()];
    }

    public boolean opaque() {
        return solid() && !transparent();
    }
}
