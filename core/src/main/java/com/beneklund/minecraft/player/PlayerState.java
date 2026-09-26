package com.beneklund.minecraft.player;

/** The saved player: feet position in world blocks, pitch and yaw in degrees. */
public record PlayerState(float x, float y, float z, float pitch, float yaw) {}
