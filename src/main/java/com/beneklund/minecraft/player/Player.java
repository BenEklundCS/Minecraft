package com.beneklund.minecraft.player;

import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.util.List;
import org.joml.Vector3f;

// The local player entity. Owns world position, orientation, and free-fly movement.
// Physics will take over movement later; for now this mirrors what Camera used to do.
public class Player implements IPhysicsBody {
    private static final float MAX_PITCH = 89.0f;
    // Scales raw mouse pixel delta to degrees of look. Player owns this since it decodes LookActions.
    private static final float MOUSE_SENSITIVITY = 0.15f;
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 1.6f;
    private static final float DEPTH = 0.6f;
    // Upward velocity applied on jump. ~9 m/s against 28 m/s² gravity clears ~1.2 blocks.
    private static final float JUMP_VELOCITY = 9.0f;
    // Eye sits above the feet (position). Matches Minecraft's 1.62 eye height.
    public static final float EYE_HEIGHT = 1.62f;

    private final Vector3f position;
    private final Vector3f velocity;
    private boolean isOnGround;
    private final float movementSpeed;
    private final Camera camera;
    private float yaw;
    private float pitch;

    public Player(Vector3f startPosition, float movementSpeed, Camera camera) {
        this.position = startPosition;
        this.velocity = new Vector3f();
        this.movementSpeed = movementSpeed;
        this.camera = camera;
    }

    @Override
    public Vector3f getPosition() {
        return this.position;
    }

    @Override
    public Vector3f getVelocity() {
        return this.velocity;
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
    public void setVelocity(Vector3f velocity) {
        this.velocity.set(velocity);
    }

    @Override
    public boolean isOnGround() {
        return this.isOnGround;
    }

    @Override
    public void setOnGround(boolean onGround) {
        this.isOnGround = onGround;
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

    // Consume this frame's input: turn movement keys into a horizontal velocity, apply
    // look, and trigger a jump. Physics integrates this velocity and resolves collisions;
    // syncCamera() runs afterward (in the game loop) once the new position is settled.
    public void tick(List<IInputAction> actions) {
        Vector3f wish = new Vector3f(); // desired horizontal heading in world space
        for (IInputAction action : actions) {
            switch (action) {
                case IInputAction.MoveActionI(float dx, float dz) -> {
                    Vector3f forward = getLookDirection();
                    forward.y = 0; // walk on the ground plane — looking up/down can't change speed
                    if (forward.lengthSquared() > 0) forward.normalize();
                    wish.fma(dz, forward).fma(dx, getRight());
                }
                case IInputAction.LookActionI(float dx, float dy) ->
                    look(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
                case IInputAction.Simple.JUMP -> {
                    if (isOnGround) velocity.y = JUMP_VELOCITY;
                }
                default -> {}
            }
        }
        // Set (not accumulate) horizontal velocity so releasing the keys stops us at once.
        // Vertical velocity is left to gravity and the jump above.
        if (wish.lengthSquared() > 0) wish.normalize().mul(movementSpeed);
        velocity.x = wish.x;
        velocity.z = wish.z;
    }

    // Apply mouse delta in degrees. -dy so mouse-up looks up; clamp pitch short of vertical.
    public void look(float dxDegrees, float dyDegrees) {
        this.yaw -= dxDegrees;
        this.pitch -= dyDegrees;
        this.pitch = Math.clamp(this.pitch, -MAX_PITCH, MAX_PITCH);
    }

    // Push the eye position and look direction into the camera. Call after movement each frame.
    public void syncCamera() {
        camera.setPosition(new Vector3f(position).add(0, EYE_HEIGHT, 0));
        camera.setFront(getLookDirection());
    }
}
