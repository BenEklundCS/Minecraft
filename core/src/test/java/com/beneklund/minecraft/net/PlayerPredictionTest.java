package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.player.MovementTuning;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.PlayerBody;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.IWorldView;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerPredictionTest {
    private static final BlockDef SOLID = new BlockDef(true, false, true, new String[0]);
    private static final BlockDef AIR = new BlockDef(false, true, true, new String[0]);
    private static final PlayerState START = new PlayerState(0.5f, 0, 0.5f, 0, 0);
    private static final PlayerIntent WALK = new PlayerIntent(0, 1, false, false, false, 0, 0);

    // Solid below y=0 and nothing else, so walking +Z never hits anything.
    private static final IWorldView OPEN_FLOOR = new IWorldView() {
        public BlockDef getBlock(int x, int y, int z) {
            return y < 0 ? SOLID : AIR;
        }

        public Chunk getChunk(ChunkPos pos) {
            return null;
        }

        public List<Entity> getEntities(AABB aabb) {
            return List.of();
        }
    };

    private static PlayerMovement movement() {
        return new PlayerMovement(new Physics(), MovementTuning.DEFAULT);
    }

    // What the server would send after running inputs 0..ackTick on its own body.
    private static IPacket.ToClient.PlayerUpdate serverStateAfter(long ackTick, PlayerIntent intent) {
        PlayerBody server = new PlayerBody(START);
        PlayerMovement movement = movement();
        for (long i = 0; i <= ackTick; i++) movement.step(server, OPEN_FLOOR, intent);
        Vector3f p = server.getPosition();
        return new IPacket.ToClient.PlayerUpdate(ackTick, p.x, p.y, p.z, server.getVelocity().y, server.isOnGround());
    }

    private static List<IPacket.ToServer.PlayerInput> walk(PlayerPrediction prediction, PlayerBody body, int steps) {
        List<IPacket.ToServer.PlayerInput> sent = new ArrayList<>();
        for (int i = 0; i < steps; i++) sent.add(prediction.step(body, OPEN_FLOOR, WALK));
        return sent;
    }

    @Test
    void stepNumbersInputsFromZero() {
        PlayerPrediction prediction = new PlayerPrediction(movement());

        List<IPacket.ToServer.PlayerInput> sent = walk(prediction, new PlayerBody(START), 3);

        assertEquals(
                List.of(0L, 1L, 2L),
                sent.stream().map(IPacket.ToServer.PlayerInput::tick).toList());
        assertTrue(sent.stream().allMatch(input -> input.intent().equals(WALK)), "each input carries its intent");
        assertEquals(3, prediction.pending());
    }

    @Test
    void updateDropsAckedInputs() {
        PlayerPrediction prediction = new PlayerPrediction(movement());
        PlayerBody body = new PlayerBody(START);
        walk(prediction, body, 3);

        prediction.reconcile(body, OPEN_FLOOR, serverStateAfter(1, WALK));

        assertEquals(1, prediction.pending(), "ticks 0 and 1 acked, tick 2 still in flight");
    }

    // Also passes against a reconcile that does nothing; it pins that a correct snap plus replay
    // lands exactly where prediction already was, bit for bit.
    @Test
    void correctUpdateLeavesBodyWhereItWas() {
        PlayerPrediction prediction = new PlayerPrediction(movement());
        PlayerBody body = new PlayerBody(START);
        walk(prediction, body, 3);
        Vector3f before = new Vector3f(body.getPosition());

        prediction.reconcile(body, OPEN_FLOOR, serverStateAfter(1, WALK));

        assertEquals(before, body.getPosition());
    }

    // The one test here that an empty reconcile fails. Without the snap the body ends 1 short;
    // snapping without the replay leaves it at tick 1's position plus 1, a step of walking short.
    @Test
    void wrongUpdateIsCorrectedThenReplayed() {
        PlayerPrediction prediction = new PlayerPrediction(movement());
        PlayerBody body = new PlayerBody(START);
        walk(prediction, body, 3);
        float expectedX = body.getPosition().x + 1;

        IPacket.ToClient.PlayerUpdate truth = serverStateAfter(1, WALK);
        prediction.reconcile(
                body,
                OPEN_FLOOR,
                new IPacket.ToClient.PlayerUpdate(
                        truth.ackTick(), truth.x() + 1, truth.y(), truth.z(), truth.vy(), truth.onGround()));

        assertEquals(expectedX, body.getPosition().x, 1e-5);
        assertEquals(serverStateAfter(2, WALK).z(), body.getPosition().z, 1e-5, "tick 2 was replayed");
    }

    // Links deliver in order today; UDP won't. An old ack must not rewind the body to an older
    // state and replay inputs that were already dropped.
    @Test
    void staleUpdateIsIgnored() {
        PlayerPrediction prediction = new PlayerPrediction(movement());
        PlayerBody body = new PlayerBody(START);
        walk(prediction, body, 6);
        prediction.reconcile(body, OPEN_FLOOR, serverStateAfter(5, WALK));
        assertEquals(0, prediction.pending(), "ack 5 covers all six inputs");
        Vector3f before = new Vector3f(body.getPosition());

        IPacket.ToClient.PlayerUpdate stale = serverStateAfter(3, WALK);
        prediction.reconcile(
                body,
                OPEN_FLOOR,
                new IPacket.ToClient.PlayerUpdate(
                        stale.ackTick(), stale.x() + 100, stale.y(), stale.z(), stale.vy(), stale.onGround()));

        assertEquals(before, body.getPosition());
        assertEquals(0, prediction.pending());
    }

    // The client owns where it's looking. Replay runs old intents, and if anything in reconcile
    // wrote their pitch/yaw, a frame with zero physics steps would rewind the camera.
    @Test
    void replayKeepsTheCurrentLook() {
        PlayerPrediction prediction = new PlayerPrediction(movement());
        PlayerBody body = new PlayerBody(START);
        walk(prediction, body, 3);
        body.setOrientation(30, 60);

        prediction.reconcile(body, OPEN_FLOOR, serverStateAfter(1, WALK));

        assertEquals(1, prediction.pending(), "reconcile ran");
        assertEquals(30f, body.pitch());
        assertEquals(60f, body.yaw());
    }
}
