package com.beneklund.minecraft.player;

import static com.beneklund.minecraft.util.Log.PLAYER;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.container.PlayerConfig;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.util.Raycast;
import com.beneklund.minecraft.util.RaycastResult;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * The local player: position, orientation, hotbar and fly mode, driven by this frame's input
 * actions.
 *
 * <p>{@link #tick} turns actions into a desired velocity, a look change, a jump and block edits.
 * {@code Physics} integrates that velocity and resolves collisions through {@link IPhysicsBody},
 * then the game loop calls {@link #syncCamera()} with the settled position.
 *
 * <p>Block edits go through {@link IWorldAuthority}. On the client that is {@code
 * ClientWorldAuthority}, which forwards them to the server.
 */
public class Player implements IPhysicsBody {
    private static final float MAX_PITCH = 89.0f;
    // Scales raw mouse pixel delta to degrees of look. Player owns this since it decodes LookActions.
    private static final float MOUSE_SENSITIVITY = 0.15f;
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 1.6f;
    private static final float DEPTH = 0.6f;
    // Eye sits above the feet (position). Matches Minecraft's 1.62 eye height.
    public static final float EYE_HEIGHT = 1.62f;

    private static final long DOUBLE_TAP_NANOS = 300_000_000L;
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

    public ChunkPos getChunkPos() {
        return ChunkPos.containing(position.x, position.z);
    }

    /**
     * The unit view vector from yaw and pitch, spherical to Cartesian. Yaw 0 faces +Z and yaw 90
     * faces +X; positive pitch looks up.
     */
    public Vector3f getLookDirection() {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vector3f(
                        (float) (Math.cos(p) * Math.sin(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.cos(y)))
                .normalize();
    }

    /** The horizontal strafe axis, {@code normalize(look x up)}. */
    public Vector3f getRight() {
        return getLookDirection().cross(new Vector3f(0, 1, 0)).normalize();
    }

    /**
     * Consumes one frame of input actions.
     *
     * <p>Raycasts from the eye for the targeted block first, so break and place act on what the
     * crosshair showed. Movement sets horizontal velocity only; vertical velocity changes on a
     * grounded jump or, in fly mode, from jump and sneak. A second jump press within 300 ms of the
     * last toggles fly mode.
     *
     * @return one {@link Interaction} per break or place action, carrying the ray that produced it
     */
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

        float hSpeed = flyMode ? FLY_SPEED : movementSpeed;
        if (wish.lengthSquared() > 0) wish.normalize().mul(hSpeed);
        velocity.x = wish.x;
        velocity.z = wish.z;

        if (flyMode) {
            if (jumpHeld) velocity.y = FLY_SPEED;
            else if (sneakHeld) velocity.y = -FLY_SPEED;
            else velocity.y = 0;
        } else {
            if (jumpHeld && isOnGround) velocity.y = jumpVelocity;
        }

        return interactions;
    }

    public boolean isFlyMode() {
        return flyMode;
    }

    /**
     * Turns by a mouse delta in degrees. Both axes are subtracted because screen y grows downward,
     * so moving the mouse up looks up. Pitch clamps at 89 degrees, since at 90 the look vector is
     * parallel to up and {@link #getRight()} degenerates to zero.
     */
    public void look(float dxDegrees, float dyDegrees) {
        yaw -= dxDegrees;
        pitch -= dyDegrees;
        pitch = Math.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    /** Moves the camera to the eye position and look direction. Call once physics has settled. */
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
