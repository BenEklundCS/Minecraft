package com.beneklund.minecraft.player;

import static com.beneklund.minecraft.util.Log.PLAYER;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.container.PlayerConfig;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.util.Raycast;
import com.beneklund.minecraft.util.RaycastResult;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3i;

// The local player entity. Owns world position, orientation, and the fly-mode toggle; Physics
// does the moving, through IPhysicsBody.
public class Player implements IPhysicsBody {
    private static final float MAX_PITCH = 89.0f;
    // Scales raw mouse pixel delta to degrees of look. Player owns this since it decodes LookActions.
    private static final float MOUSE_SENSITIVITY = 0.15f;
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 1.6f;
    private static final float DEPTH = 0.6f;
    // Eye sits above the feet (position). Matches Minecraft's 1.62 eye height.
    public static final float EYE_HEIGHT = 1.62f;

    private static final long DOUBLE_TAP_NANOS = 300_000_000L; // 300ms window
    private static final float FLY_SPEED = 50.0f;

    private boolean flyMode = false;
    private boolean wasJumpHeld = false;
    private long lastJumpPressNanos = 0L;

    private RaycastResult targetedBlock;

    private final IWorldAuthority authority;
    private final Vector3f position;
    private final Vector3f velocity;
    private boolean isOnGround;
    private final float movementSpeed;
    private final float jumpVelocity;
    private final float reach;
    private final Camera camera;
    private float yaw;
    private float pitch;

    private final Hotbar hotbar;

    public Player(PlayerConfig config, Camera camera, IWorldAuthority authority) {
        position = config.startPosition();
        velocity = new Vector3f();
        movementSpeed = config.movementSpeed();
        jumpVelocity = config.jumpVelocity();
        reach = config.reach();
        this.camera = camera;
        look(config.startYaw(), config.startPitch());
        this.authority = authority;
        hotbar = new Hotbar();
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
        return isOnGround;
    }

    @Override
    public void setOnGround(boolean onGround) {
        isOnGround = onGround;
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
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
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
    public List<Interaction> tick(List<IInputAction> actions) {
        Vector3f wish = new Vector3f(); // desired horizontal heading in world space
        boolean jumpHeld = false;
        boolean sneakHeld = false;

        Vector3f eyePos = new Vector3f(position).add(0, Player.EYE_HEIGHT, 0);
        Vector3f lookDir = this.getLookDirection();
        RaycastResult result = Raycast.cast(eyePos, lookDir, authority, reach);
        targetedBlock = result;

        List<Interaction> interactions = new ArrayList<>();
        for (IInputAction action : actions) {
            switch (action) {
                case IInputAction.MoveAction(float dx, float dz) -> {
                    Vector3f forward = getLookDirection();
                    forward.y = 0;
                    if (forward.lengthSquared() > 0) forward.normalize();
                    wish.fma(dz, forward).fma(dx, getRight());
                }
                case IInputAction.LookAction(float dx, float dy) ->
                    look(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
                case IInputAction.Simple.JUMP -> jumpHeld = true;
                case IInputAction.Simple.SNEAK -> sneakHeld = true;
                case IInputAction.Simple.BREAK_BLOCK -> {
                    this.breakTargetedBlock();
                    interactions.add(new Interaction.BlockInteraction(true, eyePos, lookDir, result));
                }
                case IInputAction.Simple.PLACE_BLOCK -> {
                    this.placeBlock();
                    interactions.add(new Interaction.BlockInteraction(false, eyePos, lookDir, result));
                }
                // Scroll wheel cycles the hotbar. Up (positive) advances, down goes back;
                // Hotbar owns the wrap-around.
                case IInputAction.ScrollAction(float delta) -> {
                    if (delta != 0) {
                        hotbar.scroll(delta > 0 ? 1 : -1);
                    }
                }
                // Number keys jump straight to a slot.
                case IInputAction.HotbarAction.Select(int slot) -> {
                    hotbar.select(slot);
                }
                default -> {}
            }
        }

        // Double-tap space toggles fly mode. Fresh press = JUMP seen this frame but not last.
        if (jumpHeld && !wasJumpHeld) {
            long now = System.nanoTime();
            if (now - lastJumpPressNanos < DOUBLE_TAP_NANOS) {
                flyMode = !flyMode;
                velocity.y = 0;
                PLAYER.info("Fly mode {}", flyMode ? "ON" : "OFF");
            }
            lastJumpPressNanos = now;
        }
        wasJumpHeld = jumpHeld;

        // Horizontal velocity — faster in fly mode.
        float hSpeed = flyMode ? FLY_SPEED : movementSpeed;
        if (wish.lengthSquared() > 0) wish.normalize().mul(hSpeed);
        velocity.x = wish.x;
        velocity.z = wish.z;

        if (flyMode) {
            // Space = ascend, shift = descend, neither = hover.
            if (jumpHeld) velocity.y = FLY_SPEED;
            else if (sneakHeld) velocity.y = -FLY_SPEED;
            else velocity.y = 0;
        } else {
            // Normal mode: jump when grounded.
            if (jumpHeld && isOnGround) velocity.y = jumpVelocity;
        }

        return interactions;
    }

    public boolean isFlyMode() {
        return flyMode;
    }

    // Apply mouse delta in degrees. -dy so mouse-up looks up; clamp pitch short of vertical.
    public void look(float dxDegrees, float dyDegrees) {
        yaw -= dxDegrees;
        pitch -= dyDegrees;
        pitch = Math.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    // Push the eye position and look direction into the camera. Call after movement each frame.
    public void syncCamera() {
        camera.setPosition(new Vector3f(position).add(0, EYE_HEIGHT, 0));
        camera.setFront(getLookDirection());
    }

    public RaycastResult getTargetedBlock() {
        return targetedBlock;
    }

    public Hotbar getHotbar() {
        return hotbar;
    }

    private void breakTargetedBlock() {
        logRaycast();
        if (!targetedBlock.hit() || !targetedBlock.hitBlock().breakable()) return;
        authority.setBlock(
                targetedBlock.blockPos().x, targetedBlock.blockPos().y, targetedBlock.blockPos().z, Block.AIR);
    }

    private void placeBlock() {
        logRaycast();

        if (!targetedBlock.hit()) return; // no op if the Player is not placing the block against another block

        Vector3i placementPosition =
                targetedBlock.blockPos().add(targetedBlock.hitFace().normal());
        if (this.getBoundingBox().getBlocksOverlapping().contains(placementPosition)) {
            PLAYER.info(
                    "Player overlaps block, cannot place at ({}, {}, {})",
                    placementPosition.x,
                    placementPosition.y,
                    placementPosition.z);
            return;
        }
        Block blockId = hotbar.blockAt(hotbar.selected());
        authority.setBlock(placementPosition.x, placementPosition.y, placementPosition.z, blockId);
    }

    private void logRaycast() {
        PLAYER.info(
                "Raycast hit={} blockPos={} face={} distance={}",
                targetedBlock.hit(),
                targetedBlock.blockPos(),
                targetedBlock.hitFace(),
                String.format("%.2f", targetedBlock.distance()));
    }
}
