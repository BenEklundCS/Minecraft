package com.beneklund.minecraft.net.replicator;

import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.PlayerSession;
import com.beneklund.minecraft.player.PlayerBody;
import java.util.List;
import org.joml.Vector3fc;

/**
 * Tells each player where the server put their body. Sent every tick, input or not, so the client
 * always has a state to reconcile against.
 */
public class PlayerStateReplicator implements IReplicator {
    @Override
    public void replicate(long tick, List<PlayerSession> joined) {
        for (PlayerSession session : joined) {
            PlayerBody body = session.body();
            if (body == null) continue;
            Vector3fc p = body.getPosition();
            session.queue(new IPacket.ToClient.PlayerUpdate(
                    session.lastInputTick(), p.x(), p.y(), p.z(), body.getVelocity().y, body.isOnGround()));
        }
    }
}
