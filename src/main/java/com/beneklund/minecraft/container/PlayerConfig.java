package com.beneklund.minecraft.container;

import org.joml.Vector3f;

// Player spawn state and movement tuning. Injected into Player so spawn position,
// initial pitch, and speed live in data without touching the player's own logic.
public record PlayerConfig(
        Vector3f startPosition, float startPitch, float movementSpeed, float jumpVelocity, float reach) {}
