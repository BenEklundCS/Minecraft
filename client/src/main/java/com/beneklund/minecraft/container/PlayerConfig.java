package com.beneklund.minecraft.container;

import org.joml.Vector3f;

/**
 * The local player's starting pose and movement tuning, injected into {@code Player}.
 *
 * <p>The start angles go through {@code Player.look} as if they were a mouse delta, which
 * subtracts them: a positive {@code startPitch} looks down, and {@code startYaw} turns the
 * opposite way to a positive yaw.
 *
 * @param startPitch degrees
 * @param reach how far in blocks the player can break or place
 */
public record PlayerConfig(
        Vector3f startPosition,
        float startPitch,
        float startYaw,
        float movementSpeed,
        float jumpVelocity,
        float reach) {
    public static final PlayerConfig DEFAULT =
            new PlayerConfig(new Vector3f(8.0f, 75.0f, -5.0f), 20.0f, 0.0f, 4.3f, 8.4f, 8.0f);
}
