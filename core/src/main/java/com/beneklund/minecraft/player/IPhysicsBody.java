package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.AABB;
import org.joml.Vector3f;

/**
 * Anything {@link Physics} can move and collide. {@code Player} is the one implementation today;
 * the interface keeps {@code Physics} free of it so mobs and projectiles can share the system.
 *
 * <p>{@link #getPosition()} is the bottom centre of the body, at the feet, and {@code Physics}
 * mutates the returned vectors in place.
 */
public interface IPhysicsBody {
    Vector3f getPosition();

    Vector3f getVelocity();

    AABB getBoundingBox();

    void setPosition(Vector3f position);

    void setOrientation(float pitch, float yaw);

    void setVelocity(Vector3f velocity);

    boolean isOnGround();

    void setOnGround(boolean onGround);
}
