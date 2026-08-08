package com.beneklund.minecraft.util;

import com.beneklund.minecraft.block.BlockDef;
import org.joml.Vector3i;

// Carries the hit block position, the block that was struck, and which face.
// hitBlock is only meaningful when hit is true. On a miss the ray ran past maxDistance,
// so blockPos is the last cell it walked and hitBlock is that cell's def — air, or null
// if the chunk isn't loaded. Check hit() before reading either.
public record RaycastResult(boolean hit, Vector3i blockPos, BlockDef hitBlock, Direction hitFace, float distance) {}
