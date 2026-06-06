package com.beneklund.minecraft.player;

import com.beneklund.minecraft.world.IWorldAuthority;

// Applies gravity, integrates velocity, and resolves AABB collisions against
// solid blocks. Operates on IPhysicsBody so it isn't tied to Player alone.
public class Physics {
    static float GRAVITY = 28.0f;

    void update(IPhysicsBody body, IWorldAuthority authority, float dt) {
        float gravity = body.getVelocity().y -= GRAVITY * dt;
    }
}
