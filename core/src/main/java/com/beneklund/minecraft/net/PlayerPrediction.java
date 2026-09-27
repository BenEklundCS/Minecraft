package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.IPhysicsBody;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.world.IWorldView;
import java.util.ArrayDeque;

public final class PlayerPrediction {
    private final PlayerMovement movement;
    private long lastAck = -1;
    private long nextTick;
    private ArrayDeque<IPacket.ToServer.PlayerInput> unacked = new ArrayDeque<>();

    public PlayerPrediction(PlayerMovement movement) {
        this.movement = movement;
    }

    /** Steps locally, remembers the input until acked, returns what to send. */
    public IPacket.ToServer.PlayerInput step(IPhysicsBody body, IWorldView world, PlayerIntent intent) {
        IPacket.ToServer.PlayerInput input = new IPacket.ToServer.PlayerInput(nextTick++, intent);
        movement.step(body, world, intent);
        unacked.add(input);
        return input;
    }

    /** Adopts the server's state as of ackTick, drops acked inputs, replays the rest. */
    public void reconcile(IPhysicsBody body, IWorldView world, IPacket.ToClient.PlayerUpdate update) {
        if (update.ackTick() < lastAck) return;
        lastAck = update.ackTick();

        while (!unacked.isEmpty() && unacked.peek().tick() <= update.ackTick()) {
            unacked.poll();
        }

        body.getPosition().set(update.x(), update.y(), update.z());
        body.getVelocity().set(0, update.vy(), 0);
        body.setOnGround(update.onGround());

        for (IPacket.ToServer.PlayerInput input : unacked) {
            movement.step(body, world, input.intent());
        }
    }

    public int pending() {
        return unacked.size();
    }

    public void reset() {
        unacked.clear();
        nextTick = 0;
        lastAck = -1;
    }
}
