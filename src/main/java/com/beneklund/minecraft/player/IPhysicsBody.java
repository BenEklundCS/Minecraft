package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.AABB;
import org.joml.Vector3f;

// Anything Physics can move and collide. Keeping this an interface lets Physics
// work against mobs and projectiles later without coupling to Player directly.
public interface IPhysicsBody {
    Vector3f getPosition();

    Vector3f getVelocity();

    AABB getBoundingBox();

    void setPosition(Vector3f position);

    void setVelocity(Vector3f velocity);

    boolean isOnGround();

    void setOnGround(boolean onGround);
}
