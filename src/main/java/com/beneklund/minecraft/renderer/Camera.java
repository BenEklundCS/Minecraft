package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.container.CameraConfig;
import com.beneklund.minecraft.container.WindowConfig;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

// Pure view/projection calculator. Holds the eye position and look direction that
// Player pushes in each frame; owns no simulation state of its own.
public class Camera {
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 1000.0f;

    private final Vector2f windowSize;
    private final Vector3f position = new Vector3f();
    private final Vector3f front = new Vector3f(0, 0, 1);
    private float fov;

    public Camera(WindowConfig config, CameraConfig cameraConfig) {
        this.windowSize = new Vector2f(config.width(), config.height());
        this.fov = cameraConfig.fov();
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setFront(Vector3f front) {
        this.front.set(front);
    }

    // Builds the view matrix from the eye position and look direction Player last pushed.
    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), new Vector3f(0, 1, 0));
    }

    // Standard perspective projection; aspect recalculated each call so setWindowSize() is always reflected.
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f()
                .perspective(
                        (float) Math.toRadians(this.fov), this.windowSize.x / this.windowSize.y, NEAR_PLANE, FAR_PLANE);
    }

    public Matrix4f getViewProjectionMatrix() {
        return getProjectionMatrix().mul(getViewMatrix());
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public void setWindowSize(float width, float height) {
        this.windowSize.set(width, height);
    }
}
