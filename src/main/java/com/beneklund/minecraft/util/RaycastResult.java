package com.beneklund.minecraft.util;

import com.beneklund.minecraft.block.BlockDef;
import org.joml.Vector3i;

// Carries the hit block position and which face was struck. Fields to be added when Raycast is implemented.
public record RaycastResult(boolean hit, Vector3i blockPos, BlockDef hitBlock, Direction hitFace, float distance) {}
