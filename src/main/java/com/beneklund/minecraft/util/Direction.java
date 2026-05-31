package com.beneklund.minecraft.util;

// Ordinal order is load-bearing: ChunkMesher indexes FACE_VERTICES and FACE_UV_FRACS
// by Direction.ordinal(). Don't reorder or insert entries without updating those arrays.
public enum Direction {
    UP, // 0
    DOWN, // 1
    NORTH, // 2
    SOUTH, // 3
    EAST, // 4
    WEST // 5
}
