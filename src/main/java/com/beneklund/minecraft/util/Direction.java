package com.beneklund.minecraft.util;

import org.joml.Vector3i;

// Ordinal order is load-bearing: ChunkMesher indexes FACE_VERTICES and FACE_UV_FRACS
// by Direction.ordinal(). Don't reorder or insert entries without updating those arrays.
public enum Direction {
    UP, // 0
    DOWN, // 1
    NORTH, // 2
    SOUTH, // 3
    EAST, // 4
    WEST; // 5

    public Vector3i normal() {
        return switch (this) {
            case UP -> new Vector3i(0, 1, 0);
            case DOWN -> new Vector3i(0, -1, 0);
            case NORTH -> new Vector3i(0, 0, -1);
            case SOUTH -> new Vector3i(0, 0, 1);
            case EAST -> new Vector3i(1, 0, 0);
            case WEST -> new Vector3i(-1, 0, 0);
        };
    }
}
