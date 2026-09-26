package com.beneklund.minecraft.util;

import org.joml.Vector3i;

/**
 * The six axis-aligned block faces, each with its unit normal.
 *
 * <p>+Y is up, -Z is north and +X is east, matching Minecraft.
 *
 * <p>Declaration order is data. The mesher writes {@link #ordinal()} into each vertex as the face
 * id, and its face tables and the terrain shader index by it, so reordering these constants
 * changes rendering.
 */
public enum Direction {
    UP(0, 1, 0), // 0
    DOWN(0, -1, 0), // 1
    NORTH(0, 0, -1), // 2
    SOUTH(0, 0, 1), // 3
    EAST(1, 0, 0), // 4
    WEST(-1, 0, 0); // 5

    /** {@link #values()} cached once, since each {@code values()} call allocates a new array. */
    public static final Direction[] DIRECTIONS = Direction.values();

    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Vector3i normal() {
        return new Vector3i(dx, dy, dz);
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }
}
