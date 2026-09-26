package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.FixedTimestep;
import com.beneklund.minecraft.world.IWorldView;
import org.joml.Vector3f;

public final class PlayerMovement {
    private final Physics physics;
    private final MovementTuning tuning;

    public PlayerMovement(Physics physics, MovementTuning tuning) {
        this.physics = physics;
        this.tuning = tuning;
    }

    /**
     * Moves body one FixedTimestep.STEP_SECONDS step. Returns nothing: like Physics.update, it
     * writes the body in place through IPhysicsBody (velocity, position, onGround).
     * Never writes orientation.
     */
    public void step(IPhysicsBody body, IWorldView world, PlayerIntent intent) {
        applyIntent(body, intent);
        physics.update(body, world, FixedTimestep.STEP_SECONDS, intent.flying());
    }

    /**
     * Writes body.getVelocity() from the intent. Reads body.isOnGround(). Touches no position,
     * no world, no dt. Package-private so the tests can check velocity before Physics changes it.
     */
    void applyIntent(IPhysicsBody body, PlayerIntent intent) {
        Vector3f wish = wishDirection(intent.moveX(), intent.moveZ(), intent.yaw());
        float hSpeed = intent.flying() ? tuning.flySpeed() : tuning.walkSpeed();
        if (wish.lengthSquared() > 0) wish.normalize().mul(hSpeed);
        body.getVelocity().x = wish.x;
        body.getVelocity().z = wish.z;

        if (intent.flying()) {
            if (intent.jump()) body.getVelocity().y = tuning.flySpeed();
            else if (intent.sneak()) body.getVelocity().y = -tuning.flySpeed();
            else body.getVelocity().y = 0;
        } else {
            if (intent.jump() && body.isOnGround()) body.getVelocity().y = tuning.jumpVelocity();
        }
    }

    /**
     * Unit world-space heading for unrotated moveX/moveZ at yaw degrees, or (0,0,0) when both
     * are zero. Returns a new vector.
     */
    static Vector3f wishDirection(float moveX, float moveZ, float yaw) {
        double r = StrictMath.toRadians(yaw);
        Vector3f forward = new Vector3f((float) StrictMath.sin(r), 0, (float) StrictMath.cos(r));
        Vector3f right = new Vector3f((float) -StrictMath.cos(r), 0, (float) StrictMath.sin(r));
        Vector3f wish = new Vector3f(moveZ * forward.x + moveX * right.x, 0, moveZ * forward.z + moveX * right.z);
        if (wish.lengthSquared() > 0) wish.normalize();
        return wish;
    }
}
