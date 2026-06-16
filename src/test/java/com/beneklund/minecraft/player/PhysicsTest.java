package com.beneklund.minecraft.player;

import static org.junit.jupiter.api.Assertions.*;

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
    private static final BlockDef SOLID = new BlockDef(true, false, new String[0]);
    private static final BlockDef AIR = new BlockDef(false, true, new String[0]);

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

            public void setBlock(int x, int y, int z, byte id) {}

            public Chunk getChunk(ChunkPos pos) {
                return null;
            }

            public List<Entity> getEntities(AABB aabb) {
                return List.of();
            }

            public void markCardinalNeighborsDirty(ChunkPos pos) {}
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
            physics.update(body, world, 0.1f);
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

        physics.update(body, world, 0.1f);

        assertEquals(0.0f, body.getVelocity().x, 1e-4, "horizontal velocity zeroed against the wall");
        // right face = position.x + halfWidth (0.3) should land on the wall face at x=1
        assertEquals(0.7f, body.getPosition().x, 1e-4, "position snapped so the body's face meets the wall");
    }
}
