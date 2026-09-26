package com.beneklund.minecraft.player;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.IWorldView;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerMovementTest {
    private static final BlockDef AIR = new BlockDef(false, true, true, new String[0]);
    private static final MovementTuning TUNING = MovementTuning.DEFAULT;

    // PhysicsTest's FakeBody, plus a count of setOrientation calls so step can be shown to leave
    // orientation alone.
    private static final class FakeBody implements IPhysicsBody {
        private final Vector3f position;
        private final Vector3f velocity;
        private boolean onGround;
        int orientationWrites;

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

        public void setOrientation(float pitch, float yaw) {
            orientationWrites++;
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

    private static final IWorldView EMPTY_WORLD = new IWorldView() {
        public BlockDef getBlock(int x, int y, int z) {
            return AIR;
        }

        public Chunk getChunk(ChunkPos pos) {
            return null;
        }

        public List<Entity> getEntities(AABB aabb) {
            return List.of();
        }
    };

    private static PlayerMovement movement() {
        return new PlayerMovement(new Physics(), TUNING);
    }

    private static PlayerIntent intent(float moveX, float moveZ, boolean jump, boolean sneak, boolean flying) {
        return new PlayerIntent(moveX, moveZ, jump, sneak, flying, 0, 0);
    }

    private static void assertVector(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, 1e-6, "x");
        assertEquals(y, actual.y, 1e-6, "y");
        assertEquals(z, actual.z, 1e-6, "z");
    }

    // wishDirection

    @Test
    void forwardAtYaw0IsPlusZ() {
        assertVector(0, 0, 1, PlayerMovement.wishDirection(0, 1, 0));
    }

    @Test
    void forwardAtYaw90IsPlusX() {
        assertVector(1, 0, 0, PlayerMovement.wishDirection(0, 1, 90));
    }

    // Matches Player.getRight() at yaw 0, so input feels the same after the move.
    @Test
    void strafeAtYaw0IsMinusX() {
        assertVector(-1, 0, 0, PlayerMovement.wishDirection(1, 0, 0));
    }

    @Test
    void diagonalIsUnitLength() {
        assertEquals(1f, PlayerMovement.wishDirection(1, 1, 37).length(), 1e-6);
    }

    // Normalising a zero vector gives NaN, which would poison velocity and then position.
    @Test
    void noMoveIsZero() {
        assertVector(0, 0, 0, PlayerMovement.wishDirection(0, 0, 45));
    }

    // applyIntent

    @Test
    void walkSetsHorizontalLeavesVertical() {
        FakeBody body = new FakeBody(new Vector3f(), new Vector3f(0, -3, 0));

        movement().applyIntent(body, intent(0, 1, false, false, false));

        assertVector(0, -3, TUNING.walkSpeed(), body.getVelocity());
    }

    @Test
    void jumpFromGroundSetsJumpVelocity() {
        FakeBody body = new FakeBody(new Vector3f(), new Vector3f());
        body.setOnGround(true);

        movement().applyIntent(body, intent(0, 0, true, false, false));

        assertEquals(8.4f, body.getVelocity().y, 1e-6);
    }

    @Test
    void jumpInAirDoesNothing() {
        FakeBody body = new FakeBody(new Vector3f(), new Vector3f(0, -3, 0));

        movement().applyIntent(body, intent(0, 0, true, false, false));

        assertEquals(-3f, body.getVelocity().y, 1e-6);
    }

    @Test
    void flyingJumpAndSneak() {
        PlayerMovement movement = movement();
        FakeBody body = new FakeBody(new Vector3f(), new Vector3f());

        movement.applyIntent(body, intent(0, 0, true, false, true));
        assertEquals(50f, body.getVelocity().y, 1e-6, "jump ascends");

        movement.applyIntent(body, intent(0, 0, false, true, true));
        assertEquals(-50f, body.getVelocity().y, 1e-6, "sneak descends");

        movement.applyIntent(body, intent(0, 0, false, false, true));
        assertEquals(0f, body.getVelocity().y, 1e-6, "neither hovers");
    }

    @Test
    void flyingUsesFlySpeedHorizontally() {
        FakeBody body = new FakeBody(new Vector3f(), new Vector3f());

        movement().applyIntent(body, intent(0, 1, false, false, true));

        Vector3f v = body.getVelocity();
        assertEquals(50f, (float) Math.hypot(v.x, v.z), 1e-4);
    }

    // step

    @Test
    void stepRunsPhysics() {
        FakeBody body = new FakeBody(new Vector3f(0, 50, 0), new Vector3f());

        movement().step(body, EMPTY_WORLD, intent(0, 0, false, false, false));

        assertTrue(body.getPosition().y < 50f, "gravity pulled the body down");
    }

    @Test
    void stepDoesNotTouchOrientation() {
        FakeBody body = new FakeBody(new Vector3f(0, 50, 0), new Vector3f());

        movement().step(body, EMPTY_WORLD, new PlayerIntent(1, 1, true, false, false, 30, 60));

        assertEquals(0, body.orientationWrites);
    }
}
