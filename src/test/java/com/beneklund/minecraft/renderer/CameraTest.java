package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.container.CameraConfig;
import com.beneklund.minecraft.container.PlayerConfig;
import com.beneklund.minecraft.container.WindowConfig;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.util.Color;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CameraTest {
    private static final WindowConfig CONFIG =
            new WindowConfig("test", 1920, 1080, false, new Color(0, 0, 0, 0), false);
    private static final float EPSILON = 1e-5f;

    private Player player;
    private Camera camera;

    @BeforeEach
    void setUp() {
        camera = new Camera(CONFIG, new CameraConfig(90f));
        // authority is null: these tests only exercise look/camera, never tick()'s raycast.
        player = new Player(new PlayerConfig(new Vector3f(0, 0, 0), 0f, 0f, 5.0f, 8.4f, 8.0f), camera, null);
    }

    @Test
    void getLookDirection_zeroPitchYaw_pointsAlongPositiveZ() {
        Vector3f dir = player.getLookDirection();
        assertEquals(0f, dir.x, EPSILON);
        assertEquals(0f, dir.y, EPSILON);
        assertEquals(1f, dir.z, EPSILON);
    }

    @Test
    void getLookDirection_isNormalized() {
        player.look(45f, 30f);
        Vector3f dir = player.getLookDirection();
        assertEquals(1f, dir.length(), EPSILON);
    }

    @Test
    void getRight_isPerpendicularToLookDirection() {
        player.look(45f, 20f);
        Vector3f look = player.getLookDirection();
        Vector3f right = player.getRight();
        assertEquals(0f, look.dot(right), EPSILON);
    }

    @Test
    void getRight_isNormalized() {
        player.look(90f, 0f);
        assertEquals(1f, player.getRight().length(), EPSILON);
    }

    @Test
    void look_pitchClampsAtPositive89() {
        player.look(0f, -200f);
        assertEquals(89f, player.getPitch(), EPSILON);
    }

    @Test
    void look_pitchClampsAtNegative89() {
        player.look(0f, 200f);
        assertEquals(-89f, player.getPitch(), EPSILON);
    }

    @Test
    void look_yawAccumulates() {
        player.look(90f, 0f);
        player.look(45f, 0f);
        assertEquals(-135f, player.getYaw(), EPSILON);
    }

    @Test
    void getProjectionMatrix_aspectRatioMatchesWindowConfig() {
        float m00Before = camera.getProjectionMatrix().m00();
        camera.setWindowSize(960, 1080);
        float m00After = camera.getProjectionMatrix().m00();
        assertEquals(m00Before * 2f, m00After, EPSILON);
    }

    @Test
    void syncCamera_afterMove_changesViewMatrix() {
        var before = camera.getViewMatrix();
        player.setPosition(new Vector3f(1f, 0f, 1f));
        player.syncCamera();
        var after = camera.getViewMatrix();
        assertNotEquals(before, after);
    }
}
