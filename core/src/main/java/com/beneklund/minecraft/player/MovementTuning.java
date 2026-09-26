package com.beneklund.minecraft.player;

public record MovementTuning(float walkSpeed, float jumpVelocity, float flySpeed) {
    public static final MovementTuning DEFAULT = new MovementTuning(4.3f, 8.4f, 50.0f);
}
