package com.beneklund.minecraft.player;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.IWorldView;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Moves an {@link IPhysicsBody} one fixed step: gravity, velocity integration, and collision
 * against solid blocks.
 *
 * <p>Collision is discrete and per axis, in the order X, Z, Y. Each axis moves the body by {@code
 * velocity * dt}, then checks every cell its {@link AABB} overlaps; if any is solid, the body snaps
 * flush against the first solid cell in the direction of travel and that axis's velocity is zeroed.
 * Axes are resolved separately so a diagonal move into a wall stops on one axis and slides along
 * the other. Landing on Y is what sets {@link IPhysicsBody#setOnGround}.
 *
 * <p>The check samples the end position, so a body moving more than a block per step could pass
 * through a one-block wall. {@code TERMINAL_VELOCITY} of 40 blocks/s at the 60 Hz {@code
 * FixedTimestep} moves at most 0.67 blocks per step on Y.
 *
 * <p>Units are blocks and seconds. Jump peak height is {@code v * v / (2 * GRAVITY)}; the jump
 * velocity and walk speed live in {@code PlayerConfig}.
 *
 * <p>Fly mode integrates velocity with no gravity and no collision.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection">
 *     MDN: 3D collision detection</a>
 */
public class Physics {
    private static final float GRAVITY = 32.0f;
    private static final float TERMINAL_VELOCITY = 40.0f;

    /** Advances {@code body} by {@code dt} seconds, with collision unless {@code flying}. */
    public void update(IPhysicsBody body, IWorldView world, float dt, boolean flying) {
        if (flying) {
            fly(body, dt);
        } else {
            nofly(body, world, dt);
        }
    }

    private void fly(IPhysicsBody body, float dt) {
        body.setOnGround(false);
        body.getPosition().add(body.getVelocity().x * dt, body.getVelocity().y * dt, body.getVelocity().z * dt);
    }

    private void nofly(IPhysicsBody body, IWorldView world, float dt) {
        Vector3f velocity = body.getVelocity();
        velocity.y -= GRAVITY * dt;
        if (velocity.y < -TERMINAL_VELOCITY) velocity.y = -TERMINAL_VELOCITY;

        // assume we're airborne; the downward Y sweep re-proves contact if we land
        body.setOnGround(false);
        resolveX(body, world, dt);
        resolveZ(body, world, dt);
        resolveY(body, world, dt);
    }

    private void resolveX(IPhysicsBody body, IWorldView world, float dt) {
        Vector3f position = body.getPosition();
        Vector3f velocity = body.getVelocity();
        position.x += velocity.x * dt;

        AABB box = body.getBoundingBox();
        float halfWidth = (box.maxX() - box.minX()) / 2f; // position sits at the horizontal center

        // Same trap resolveY had: getBlocksOverlapping() walks x ascending, so breaking on the
        // first solid cell always takes the lowest one. That's the face we actually hit only
        // when moving +x; going -x into something two cells deep snapped us to the far face.
        // Take the extreme cell in the direction of travel instead.
        boolean movingNegative = velocity.x < 0;
        boolean hit = false;
        int hitX = 0;
        for (Vector3i cell : box.getBlocksOverlapping()) {
            if (!isSolid(world, cell)) continue;
            if (!hit || (movingNegative ? cell.x > hitX : cell.x < hitX)) hitX = cell.x;
            hit = true;
        }
        if (!hit) return;

        if (velocity.x > 0) {
            position.x = hitX - halfWidth; // snap our right face to the block's left face
        } else if (velocity.x < 0) {
            position.x = hitX + 1 + halfWidth; // snap our left face to the block's right face
        }
        velocity.x = 0;
    }

    private void resolveZ(IPhysicsBody body, IWorldView world, float dt) {
        Vector3f position = body.getPosition();
        Vector3f velocity = body.getVelocity();
        position.z += velocity.z * dt;

        AABB box = body.getBoundingBox();
        float halfDepth = (box.maxZ() - box.minZ()) / 2f;

        // Same reasoning as resolveX above.
        boolean movingNegative = velocity.z < 0;
        boolean hit = false;
        int hitZ = 0;
        for (Vector3i cell : box.getBlocksOverlapping()) {
            if (!isSolid(world, cell)) continue;
            if (!hit || (movingNegative ? cell.z > hitZ : cell.z < hitZ)) hitZ = cell.z;
            hit = true;
        }
        if (!hit) return;

        if (velocity.z > 0) {
            position.z = hitZ - halfDepth;
        } else if (velocity.z < 0) {
            position.z = hitZ + 1 + halfDepth;
        }
        velocity.z = 0;
    }

    private void resolveY(IPhysicsBody body, IWorldView world, float dt) {
        Vector3f position = body.getPosition();
        Vector3f velocity = body.getVelocity();
        position.y += velocity.y * dt;

        AABB box = body.getBoundingBox();
        float height = box.maxY() - box.minY(); // position.y is the feet; the box extends up

        // Take the cell we actually hit, not the first one iteration happens to reach.
        // getBlocksOverlapping() walks y ascending, so breaking on the first solid cell
        // gives the lowest one — fine going up into a ceiling, wrong coming down. One
        // slow tick drops us through two solid cells at once, and snapping to the lower
        // cell's top face leaves the feet a block inside the floor. Falling wants the
        // highest solid cell, rising wants the lowest.
        boolean falling = velocity.y < 0;
        boolean hit = false;
        int hitY = 0;
        for (Vector3i cell : box.getBlocksOverlapping()) {
            if (!isSolid(world, cell)) continue;
            if (!hit || (falling ? cell.y > hitY : cell.y < hitY)) hitY = cell.y;
            hit = true;
        }
        if (!hit) return;

        if (velocity.y > 0) {
            position.y = hitY - height; // bonked our head: top face to the block's bottom
        } else if (velocity.y < 0) {
            position.y = hitY + 1; // landed: feet to the block's top face
            body.setOnGround(true);
        }
        velocity.y = 0;
    }

    private boolean isSolid(IWorldView world, Vector3i cell) {
        BlockDef block = world.getBlock(cell.x, cell.y, cell.z);
        return block != null && block.solid();
    }
}
