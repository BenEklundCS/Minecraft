package com.beneklund.minecraft.util;

import org.joml.Vector3i;

public enum Direction {
    UP(0, 1, 0), // 0
    DOWN(0, -1, 0), // 1
    NORTH(0, 0, -1), // 2
    SOUTH(0, 0, 1), // 3
    EAST(1, 0, 0), // 4
    WEST(-1, 0, 0); // 5

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
