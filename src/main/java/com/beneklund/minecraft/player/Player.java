package com.beneklund.minecraft.player;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import org.joml.Vector3f;

// The local player entity. Owns world position, orientation, and free-fly movement.
// Physics will take over movement later; for now this mirrors what Camera used to do.
public class Player implements IPhysicsBody {
    private static final float MAX_PITCH = 89.0f;

    private final Vector3f position;
    private final float movementSpeed;
    private float yaw;
    private float pitch;

    public Player(Vector3f startPosition, float movementSpeed) {
        this.position = startPosition;
        this.movementSpeed = movementSpeed;
    }

    // Returns position by reference — Camera reads this every frame to build the view matrix.
    public Vector3f getPosition() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    // Converts world position to chunk grid coordinates using floor division so negative coords map correctly.
    public ChunkPos getChunkPos() {
        return new ChunkPos(
                Math.floorDiv((int) position.x, Chunk.SIZE_XZ), Math.floorDiv((int) position.z, Chunk.SIZE_XZ));
    }

    // Spherical -> cartesian from yaw/pitch. Yaw=0 faces +Z; yaw grows clockwise.
    public Vector3f getLookDirection() {
        double y = Math.toRadians(this.yaw);
        double p = Math.toRadians(this.pitch);
        return new Vector3f(
                        (float) (Math.cos(p) * Math.sin(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.cos(y)))
                .normalize();
    }

    // Right vector for strafing: cross(look, up), normalized.
    public Vector3f getRight() {
        return getLookDirection().cross(new Vector3f(0, 1, 0)).normalize();
    }

    // Free-fly: move along look (forward/back) and right (strafe), scaled by speed and dt.
    public void moveRelative(float forward, float right, float dt) {
        position.fma(forward * movementSpeed * dt, getLookDirection());
        position.fma(right * movementSpeed * dt, getRight());
    }

    // Apply mouse delta in degrees. -dy so mouse-up looks up; clamp pitch short of vertical.
    public void look(float dxDegrees, float dyDegrees) {
        this.yaw -= dxDegrees;
        this.pitch -= dyDegrees;
        this.pitch = Math.clamp(this.pitch, -MAX_PITCH, MAX_PITCH);
    }
}
