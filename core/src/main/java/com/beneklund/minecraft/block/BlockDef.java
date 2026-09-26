package com.beneklund.minecraft.block;

import com.beneklund.minecraft.util.Direction;

/**
 * A block type's properties: collision, rendering, breakability, emitted light and a texture per
 * face.
 *
 * @param solid collides with bodies and stops raycasts
 * @param transparent lets neighbours show through, so the mesher keeps the faces of blocks next to
 *     it; leaves are solid and transparent
 * @param blended drawn in the alpha-blended pass after all opaque geometry, as water is
 * @param breakable the player can break it; false only for bedrock
 * @param lightLevel block light emitted, 0 to 15
 * @param tileNames atlas tile per face, indexed by {@link Direction#ordinal()}; build it with
 *     {@link #build} so the order can't drift from {@code Direction}
 */
public record BlockDef(
        boolean solid, boolean transparent, boolean blended, boolean breakable, int lightLevel, String[] tileNames) {

    public BlockDef(boolean solid, boolean transparent, boolean breakable, String[] tileNames) {
        this(solid, transparent, false, breakable, 0, tileNames);
    }

    /** A copy drawn in the blended pass. */
    public BlockDef withBlending() {
        return new BlockDef(solid, transparent, true, breakable, lightLevel, tileNames);
    }

    /** A copy that emits block light at {@code level}. */
    public BlockDef withLight(int level) {
        return new BlockDef(solid, transparent, blended, breakable, level, tileNames);
    }

    /**
     * A non-blended, non-emitting block with a named tile per face. Each face is written to its
     * {@link Direction#ordinal()} slot by name, so reordering {@code Direction} can't swap
     * textures.
     */
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

    /** Solid and not transparent. {@code LightEngine} stops light at opaque blocks. */
    public boolean opaque() {
        return solid() && !transparent();
    }
}
