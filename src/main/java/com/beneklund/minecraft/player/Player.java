package com.beneklund.minecraft.player;

import static com.beneklund.minecraft.util.Log.LOGGER;

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
import java.util.Map;
import org.joml.Vector3f;
import org.joml.Vector3i;

// The local player entity. Owns world position, orientation, and free-fly movement.
// Physics will take over movement later; for now this mirrors what Camera used to do.
public class Player implements IPhysicsBody {
    private static final float MAX_PITCH = 89.0f;
    // Scales raw mouse pixel delta to degrees of look. Player owns this since it decodes LookActions.
    private static final float MOUSE_SENSITIVITY = 0.15f;
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 1.6f;
    private static final float DEPTH = 0.6f;
    // Eye sits above the feet (position). Matches Minecraft's 1.62 eye height.
    public static final float EYE_HEIGHT = 1.62f;

    private RaycastResult targetedBlock;

    private final Map<Integer, Block> slotToBlockIdHotbar = Map.ofEntries(
            Map.entry(1, Block.STONE),
            Map.entry(2, Block.DIRT),
            Map.entry(3, Block.GRASS),
            Map.entry(4, Block.BEDROCK),
            Map.entry(5, Block.SAND),
            Map.entry(6, Block.GRAVEL),
            Map.entry(7, Block.OAK_LOG),
            Map.entry(8, Block.OAK_PLANK),
            Map.entry(9, Block.OAK_LEAF));

    // 0-indexed hotbar selection (slot 0 = key '1', matching HotbarActionI.Select). The
    // palette map above is keyed 1-9, so look-ups add 1. Scroll and number keys move this.
    private int selectedSlot = 0;

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

    public Player(PlayerConfig config, Camera camera, IWorldAuthority authority) {
        position = config.startPosition();
        velocity = new Vector3f();
        movementSpeed = config.movementSpeed();
        jumpVelocity = config.jumpVelocity();
        reach = config.reach();
        this.camera = camera;
        look(0, config.startPitch());
        this.authority = authority;
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

        Vector3f eyePos = new Vector3f(position).add(0, Player.EYE_HEIGHT, 0);
        Vector3f lookDir = this.getLookDirection();
        RaycastResult result = Raycast.cast(eyePos, lookDir, authority, reach);
        targetedBlock = result;

        List<Interaction> interactions = new ArrayList<>();
        for (IInputAction action : actions) {
            switch (action) {
                case IInputAction.MoveActionI(float dx, float dz) -> {
                    Vector3f forward = getLookDirection();
                    forward.y = 0;
                    if (forward.lengthSquared() > 0) forward.normalize();
                    wish.fma(dz, forward).fma(dx, getRight());
                }
                case IInputAction.LookActionI(float dx, float dy) ->
                    look(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
                case IInputAction.Simple.JUMP -> {
                    if (isOnGround) velocity.y = jumpVelocity;
                }
                case IInputAction.Simple.BREAK_BLOCK -> {
                    this.breakTargetedBlock();
                    interactions.add(new Interaction.BlockInteraction(true, eyePos, lookDir, result));
                }
                case IInputAction.Simple.PLACE_BLOCK -> {
                    this.placeBlock();
                    interactions.add(new Interaction.BlockInteraction(false, eyePos, lookDir, result));
                }
                // Scroll wheel cycles the hotbar; wrap around using the palette size so it
                // stays correct if the palette grows. Up (positive) advances, down goes back.
                case IInputAction.ScrollActionI(float delta) -> {
                    if (delta != 0) {
                        int step = delta > 0 ? 1 : -1;
                        selectedSlot = Math.floorMod(selectedSlot + step, slotToBlockIdHotbar.size());
                        LOGGER.info(
                                "Selected slot {} -> {}",
                                selectedSlot,
                                slotToBlockIdHotbar.get(selectedSlot + 1).name());
                    }
                }
                // Number keys jump straight to a slot.
                case IInputAction.HotbarActionI.Select(int slot) -> {
                    selectedSlot = slot;
                    LOGGER.info(
                            "Selected slot {} -> {}",
                            selectedSlot,
                            slotToBlockIdHotbar.get(selectedSlot + 1).name());
                }
                default -> {}
            }
        }
        // Set (not accumulate) horizontal velocity so releasing the keys stops us at once.
        // Vertical velocity is left to gravity and the jump above.
        if (wish.lengthSquared() > 0) wish.normalize().mul(movementSpeed);
        velocity.x = wish.x;
        velocity.z = wish.z;
        return interactions;
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

    // 0-indexed currently-selected hotbar slot, for the HUD to highlight.
    public int getSelectedSlot() {
        return selectedSlot;
    }

    private void breakTargetedBlock() {
        logRaycast();
        authority.setBlock(
                targetedBlock.blockPos().x, targetedBlock.blockPos().y, targetedBlock.blockPos().z, Block.AIR);
    }

    private void placeBlock() {
        logRaycast();
        Vector3i placementPosition =
                targetedBlock.blockPos().add(targetedBlock.hitFace().normal());
        if (this.getBoundingBox().getBlocksOverlapping().contains(placementPosition)) {
            LOGGER.info(
                    "Player overlaps block, cannot place at ({}, {}, {})",
                    placementPosition.x,
                    placementPosition.y,
                    placementPosition.z);
            return;
        }
        Block blockId = slotToBlockIdHotbar.getOrDefault(selectedSlot + 1, Block.STONE);
        authority.setBlock(placementPosition.x, placementPosition.y, placementPosition.z, blockId);
    }

    private void logRaycast() {
        LOGGER.info(
                "Raycast hit={} blockPos={} face={} distance={}",
                targetedBlock.hit(),
                targetedBlock.blockPos(),
                targetedBlock.hitFace(),
                String.format("%.2f", targetedBlock.distance()));
    }
}
