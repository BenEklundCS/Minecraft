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

    // Fly mode used to be integrated inline in Game, which meant two places moved the player and
    // only one of them was Physics. These four pin the flying branch hard enough that nothing
    // outside Physics needs to move a body again — that's what "exactly one code path" buys.

    // The regression that matters. Player.tick sets velocity.y = 0 when you're hovering (neither
    // ascending nor descending), so if gravity is applied before the flying branch you accumulate
    // -GRAVITY*dt every tick and sink about half a block per second while apparently holding still.
    @Test
    void flying_hovering_holdsAltitude() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0.5f, 80, 0.5f), new Vector3f());
        IWorldAuthority world = worldWhere(cell -> cell.y < 0);

        for (int i = 0; i < 60; i++) {
            body.getVelocity().set(0, 0, 0); // what Player.tick does every frame while hovering
            physics.update(body, world, 1f / 60f, true);
        }

        assertEquals(80f, body.getPosition().y, 1e-4, "a hovering second must not lose altitude");
    }

    // Ascending is where a gravity leak hides: 50 - 32*(1/60) is still ~49.5, so the position looks
    // right and only the velocity shows the drag. Assert the velocity too.
    @Test
    void flying_ascending_isNotDraggedDownByGravity() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0.5f, 80, 0.5f), new Vector3f(0, 50, 0));
        IWorldAuthority world = worldWhere(cell -> cell.y < 0);

        physics.update(body, world, 0.1f, true);

        assertEquals(85f, body.getPosition().y, 1e-4, "moved by exactly velocity * dt");
        assertEquals(50f, body.getVelocity().y, 1e-4, "velocity untouched — no gravity in fly mode");
    }

    // No collision either: fly mode is noclip, so a solid world doesn't stop or snap the body.
    @Test
    void flying_passesThroughSolidBlocks() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0.5f, 50, 0.5f), new Vector3f(10, 0, 0));
        IWorldAuthority world = worldWhere(cell -> true); // solid everywhere

        physics.update(body, world, 0.1f, true);

        assertEquals(1.5f, body.getPosition().x, 1e-4, "no snapping to a face");
        assertEquals(10f, body.getVelocity().x, 1e-4, "no velocity zeroed against a wall");
    }

    // Physics is still the thing that moves the body in fly mode — the point of the whole change.
    // A body with velocity on all three axes advances on all three.
    @Test
    void flying_movesOnEveryAxis() {
        Physics physics = new Physics();
        FakeBody body = new FakeBody(new Vector3f(0, 50, 0), new Vector3f(3, -4, 5));
        IWorldAuthority world = worldWhere(cell -> cell.y < 0);

        physics.update(body, world, 2f, true);

        assertEquals(6f, body.getPosition().x, 1e-4);
        assertEquals(42f, body.getPosition().y, 1e-4);
        assertEquals(10f, body.getPosition().z, 1e-4);
    }
}
