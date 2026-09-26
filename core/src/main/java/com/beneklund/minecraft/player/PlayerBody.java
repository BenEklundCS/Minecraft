package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.AABB;
import org.joml.Vector3f;

public final class PlayerBody implements IPhysicsBody {
    public static final float WIDTH = 0.6f, HEIGHT = 1.6f, DEPTH = 0.6f;

    public PlayerBody(PlayerState at) {}

    // IPhysicsBody: live Vector3f getters, same contract as Player

    public float pitch() {
        return 0.0f;
    }

    public float yaw() {
        return 0.0f;
    }

    public PlayerState state() {
        return new PlayerState(0, 0, 0, 0, 0);
    }

    @Override
    public Vector3f getPosition() {
        return null;
    }

    @Override
    public Vector3f getVelocity() {
        return null;
    }

    @Override
    public AABB getBoundingBox() {
        return null;
    }

    @Override
    public void setPosition(Vector3f position) {}

    @Override
    public void setOrientation(float pitch, float yaw) {}

    @Override
    public void setVelocity(Vector3f velocity) {}

    @Override
    public boolean isOnGround() {
        return false;
    }

    @Override
    public void setOnGround(boolean onGround) {}
}
