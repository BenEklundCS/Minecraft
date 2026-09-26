package com.beneklund.minecraft.player;

public record PlayerIntent(
        float moveX, float moveZ, boolean jump, boolean sneak, boolean flying, float pitch, float yaw) {}
