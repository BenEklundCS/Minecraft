package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.container.WindowConfig;
import com.beneklund.minecraft.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector2f;

// Pure view/projection calculator. Reads player position and orientation each frame;
// owns no simulation state — Player drives everything.
public class Camera {
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 1000.0f;

    private final Vector2f windowSize;
    private final Player player;
    private float fov;

    public Camera(WindowConfig config, Player player, float fov) {
        this.windowSize = new Vector2f(config.width(), config.height());
        this.player = player;
        this.fov = fov;
    }

    // Builds view matrix from player's current position and look direction.
    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(
                player.getPosition(),
                new org.joml.Vector3f(player.getPosition()).add(player.getLookDirection()),
                new org.joml.Vector3f(0, 1, 0));
    }

    // Standard perspective projection; aspect recalculated each call so setWindowSize() is always reflected.
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f().perspective(
                (float) Math.toRadians(this.fov),
                this.windowSize.x / this.windowSize.y,
                NEAR_PLANE, FAR_PLANE);
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public void setWindowSize(float width, float height) {
        this.windowSize.x = width;
        this.windowSize.y = height;
    }
}
