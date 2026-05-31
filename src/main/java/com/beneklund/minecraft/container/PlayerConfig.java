package com.beneklund.minecraft.container;

// Movement tuning for the player/camera. Injected into Camera so speed is
// configurable without touching the camera's own logic.
public record PlayerConfig(float movementSpeed) {}
