package com.beneklund.minecraft.player;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.container.CameraConfig;
import com.beneklund.minecraft.container.PlayerConfig;
import com.beneklund.minecraft.container.WindowConfig;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.input.IInputAction;
import com.beneklund.minecraft.renderer.camera.Camera;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PlayerIntentTest {
    private static final WindowConfig CONFIG =
            new WindowConfig("test", 1920, 1080, false, WindowConfig.Mode.WINDOWED, new Color(0, 0, 0, 0), false);
    private static BlockRegistry registry = BlockRegistry.createDefault();

    private class TestWorldAuthority implements IWorldAuthority {
        @Override
        public void setBlock(int x, int y, int z, Block block) {}

        @Override
        public void markNeighborsDirty(ChunkPos pos) {}

        @Override
        public BlockDef getBlock(int x, int y, int z) {
            return registry.get(Block.AIR);
        }

        @Override
        public Chunk getChunk(ChunkPos pos) {
            return null;
        }

        @Override
        public List<Entity> getEntities(AABB aabb) {
            return List.of();
        }
    }

    @Test
    void intentIsNonNullIdleAndCarriesPlayerConfig() {
        Camera camera = new Camera(CONFIG, new CameraConfig(90f));
        Player player = new Player(PlayerConfig.DEFAULT, camera, null);
        assertNotNull(player.intent());
        assertEquals(player.intent(), new PlayerIntent(0, 0, false, false, false, player.getPitch(), player.getYaw()));
    }

    private Player ticking() {
        return new Player(PlayerConfig.DEFAULT, new Camera(CONFIG, new CameraConfig(90f)), new TestWorldAuthority());
    }

    // Summed raw: PlayerMovement.wishDirection normalises, so a diagonal here is (1, 1), not unit length.
    @Test
    void moveActionsSumIntoMoveXAndMoveZ() {
        Player player = ticking();

        player.tick(List.of(new IInputAction.MoveAction(1, 0), new IInputAction.MoveAction(0, 1)));

        assertEquals(1f, player.intent().moveX());
        assertEquals(1f, player.intent().moveZ());
    }

    // The server steps with the intent's look, so it has to be the look the client ended the frame
    // on, or the two sides walk in different directions.
    @Test
    void lookInTheSameTickCarriesThePostLookOrientation() {
        Player player = ticking();
        float startYaw = player.getYaw();

        player.tick(List.of(new IInputAction.LookAction(100, 50)));

        assertNotEquals(startYaw, player.intent().yaw(), "look changed the yaw");
        assertEquals(player.getYaw(), player.intent().yaw());
        assertEquals(player.getPitch(), player.intent().pitch());
    }

    @Test
    void jumpAndSneakHeldSetTheirFlags() {
        Player player = ticking();

        player.tick(List.of(IInputAction.Simple.JUMP, IInputAction.Simple.SNEAK));

        assertTrue(player.intent().jump());
        assertTrue(player.intent().sneak());
    }

    // Each tick builds a fresh intent from that frame's actions; nothing held last frame leaks in.
    @Test
    void anEmptyTickIsIdle() {
        Player player = ticking();
        player.tick(List.of(new IInputAction.MoveAction(1, 1), IInputAction.Simple.JUMP, IInputAction.Simple.SNEAK));

        player.tick(List.of());

        PlayerIntent intent = player.intent();
        assertEquals(0f, intent.moveX());
        assertEquals(0f, intent.moveZ());
        assertFalse(intent.jump());
        assertFalse(intent.sneak());
    }

    // The intent is built after the double-tap check, so the step that runs this frame already flies.
    // Relies on three ticks finishing inside the 300 ms double-tap window.
    @Test
    void doubleTapFliesInTheSameTick() {
        Player player = ticking();

        player.tick(List.of(IInputAction.Simple.JUMP));
        player.tick(List.of());
        player.tick(List.of(IInputAction.Simple.JUMP));

        assertTrue(player.isFlyMode());
        assertTrue(player.intent().flying());
    }
}
