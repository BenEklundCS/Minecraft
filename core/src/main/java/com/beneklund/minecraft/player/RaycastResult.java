package com.beneklund.minecraft.player;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.util.Direction;
import org.joml.Vector3i;

/**
 * The outcome of {@link Raycast#cast}: the struck cell, its block, the face the ray entered
 * through, and the distance along the ray.
 *
 * <p>Check {@link #hit()} first. On a miss the ray ran past its maximum distance, {@code blockPos}
 * is the last cell it walked, and {@code hitBlock} is that cell's block: air, or {@code null} if
 * the chunk isn't loaded.
 */
public record RaycastResult(boolean hit, Vector3i blockPos, BlockDef hitBlock, Direction hitFace, float distance) {}
