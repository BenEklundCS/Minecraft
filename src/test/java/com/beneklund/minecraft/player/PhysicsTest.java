package com.beneklund.minecraft.player;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import java.util.List;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

class PhysicsTest {
    private static final BlockDef SOLID = new BlockDef(true, false, true, new String[0]);
    private static final BlockDef AIR = new BlockDef(false, true, true, new String[0]);

    // Minimal IPhysicsBody with the player's footprint (0.6 x 1.6 x 0.6). getPosition/
    // getVelocity hand back the live vectors so Physics can mutate them in place.
    private static final class FakeBody implements IPhysicsBody {
        private final Vector3f position;
        private final Vector3f velocity;
        private boolean onGround;

        FakeBody(Vector3f position, Vector3f velocity) {
            this.position = position;
            this.velocity = velocity;
        }

        public Vector3f getPosition() {
            return position;
        }

        public Vector3f getVelocity() {
            return velocity;
        }

        public AABB getBoundingBox() {
            return AABB.ofSize(position, 0.6f, 1.6f, 0.6f);
        }

        public void setPosition(Vector3f p) {
            position.set(p);
        }

        // Physics never reads orientation, so these tests don't track it.
        public void setOrientation(float pitch, float yaw) {}

        public void setVelocity(Vector3f v) {
            velocity.set(v);
        }

        public boolean isOnGround() {
            return onGround;
        }

        public void setOnGround(boolean onGround) {
            this.onGround = onGround;
        }
    }

    // A world where a cell is solid iff the predicate says so. Only getBlock is exercised.
    private static IWorldAuthority worldWhere(Predicate<Vector3i> solid) {
        return new IWorldAuthority() {
            public BlockDef getBlock(int x, int y, int z) {
                return solid.test(new Vector3i(x, y, z)) ? SOLID : AIR;
            }

            public void setBlock(int x, int y, int z, Block block) {}

            public Chunk getChunk(ChunkPos pos) {
                return null;
            }

            public List<Entity> getEntities(AABB aabb) {
                return List.of();
            }

            public void markNeighborsDirty(ChunkPos pos) {}
        };
    }

    // 15.T1 — drop from 5 blocks up onto a floor (everything below y=0 is solid);
    // after 10 ticks the body has landed, rests at y=0, and reports onGround.
    @Test
    void gravity_fallsAndStopsOnFloor() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0, 5, 0), new Vector3f());
        IWorldAuthority world = worldWhere(cell -> cell.y < 0);

        for (int i = 0; i < 10; i++) {
            physics.update(body, world, 0.1f, false);
        }

        assertEquals(0.0f, body.getPosition().y, 1e-4, "feet should rest on the floor's top face");
        assertEquals(0.0f, body.getVelocity().y, 1e-4, "vertical velocity zeroed on landing");
        assertTrue(body.isOnGround(), "body should report standing on ground");
    }

    // 15.T2 — walk into a wall (everything at x>=1 is solid). The X sweep zeroes
    // velocity.x and snaps the body's right face flush against the block face at x=1.
    @Test
    void wall_blocksHorizontalMovement() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0.5f, 50, 0.5f), new Vector3f(10, 0, 0));
        IWorldAuthority world = worldWhere(cell -> cell.x >= 1);

        physics.update(body, world, 0.1f, false);

        assertEquals(0.0f, body.getVelocity().x, 1e-4, "horizontal velocity zeroed against the wall");
        // right face = position.x + halfWidth (0.3) should land on the wall face at x=1
        assertEquals(0.7f, body.getPosition().x, 1e-4, "position snapped so the body's face meets the wall");
    }

    // The resolveY bug, mirrored on X. getBlocksOverlapping() walks x ascending, so breaking
    // on the first solid cell always took the lowest — right when moving +x (covered above),
    // wrong when moving -x. One fast tick spans two solid cells and the old code snapped to
    // the far one, leaving the body embedded in the near block.
    @Test
    void wallOnTheLeft_movingNegativeXSnapsToTheNearFace() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(3f, 50, 0.5f), new Vector3f(-20, 0, 0));
        IWorldAuthority world = worldWhere(cell -> cell.x <= 1); // slab two cells deep: x=0 and x=1

        physics.update(body, world, 0.1f, false); // moves -2.0 in one tick, spanning both cells

        assertEquals(0.0f, body.getVelocity().x, 1e-4, "horizontal velocity zeroed against the wall");
        // near face is cell 1's right side at x=2; left face = position.x - halfWidth (0.3)
        assertEquals(2.3f, body.getPosition().x, 1e-4, "snapped to the near face, not through to cell 0");
    }

    // Same bug on Z.
    @Test
    void wallBehind_movingNegativeZSnapsToTheNearFace() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0.5f, 50, 3f), new Vector3f(0, 0, -20));
        IWorldAuthority world = worldWhere(cell -> cell.z <= 1);

        physics.update(body, world, 0.1f, false);

        assertEquals(0.0f, body.getVelocity().z, 1e-4, "depth velocity zeroed against the wall");
        assertEquals(2.3f, body.getPosition().z, 1e-4, "snapped to the near face, not through to cell 0");
    }
}
