package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.AABB;
import org.joml.Vector3f;

public final class PlayerBody implements IPhysicsBody {
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.6f;
    public static final float DEPTH = 0.6f;

    private final Vector3f position;
    private final Vector3f velocity;
    private float pitch;
    private float yaw;
    private boolean onGround;

    public PlayerBody(PlayerState at) {
        position = new Vector3f(at.x(), at.y(), at.z());
        velocity = new Vector3f();
        pitch = at.pitch();
        yaw = at.yaw();
        onGround = false;
    }

    public float pitch() {
        return pitch;
    }

    public float yaw() {
        return yaw;
    }

    public PlayerState state() {
        return new PlayerState(position.x, position.y, position.z, pitch, yaw);
    }

    @Override
    public Vector3f getPosition() {
        return position;
    }

    @Override
    public Vector3f getVelocity() {
        return velocity;
    }

    @Override
    public AABB getBoundingBox() {
        return AABB.ofSize(position, WIDTH, HEIGHT, DEPTH);
    }

    @Override
    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    @Override
    public void setOrientation(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
    }

    @Override
    public void setVelocity(Vector3f velocity) {
        this.velocity.set(velocity);
    }

    @Override
    public boolean isOnGround() {
        return onGround;
    }

    @Override
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
