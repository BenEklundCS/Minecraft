package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.window.WindowConfig;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class Camera {
    private final Vector2f windowSize;
    private final Vector3f position;
    // private final Vector3f target;
    private final Vector3f up;

    private float yaw;
    private float pitch;
    private float fov;

    public Camera(WindowConfig config, Vector3f position, float fov) {
        this.windowSize = new Vector2f(config.width(), config.height());
        this.position = position;
        // this.target = new Vector3f(0.0f, 0.0f, 0.0f);
        this.up = new Vector3f(0.0f, 1.0f, 0.0f);
        this.fov = fov;
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(this.position, new Vector3f(this.position).add(getLookDirection()), this.up);
        // orbit: return new Matrix4f().lookAt(this.position, this.target, this.up);
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f()
                .perspective((float) Math.toRadians(this.fov), this.windowSize.x / this.windowSize.y, 0.1f, 1000f);
    }

    public float getFov() {
        return this.fov;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public void setWindowSize(float width, float height) {
        this.windowSize.x = width;
        this.windowSize.y = height;
    }

    // Orbit just moves the position each frame; getViewMatrix() keeps looking at the fixed target.
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public void setPosition(Vector3f position) {
        this.position.x = position.x;
        this.position.y = position.y;
        this.position.z = position.z;
    }

    /*
     * Facing direction as a unit vector, from yaw/pitch (spherical -> cartesian). Yaw is measured
     * swap getViewMatrix() over to lookAt(position, position + getLookDirection(), up) for free-look.
     */
    public Vector3f getLookDirection() {
        double y = Math.toRadians(this.yaw);
        double p = Math.toRadians(this.pitch);
        return new Vector3f(
                        (float) (Math.cos(p) * Math.sin(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.cos(y)))
                .normalize();
    }

    // Right vector for strafing: perpendicular to look and up, from their cross product.
    public Vector3f getRight() {
        return getLookDirection().cross(this.up).normalize();
    }

    /*
     * Apply a frame of mouse movement (already scaled by sensitivity). -dy so mouse-up looks up.
     * Clamp pitch short of vertical (±89) or look aligns with up, getRight() collapses, and the
     * view flips.
     */
    public void look(float dxDegrees, float dyDegrees) {
        this.yaw -= dxDegrees;
        this.pitch -= dyDegrees;
        this.pitch = Math.clamp(this.pitch, -89.0f, 89.0f);
    }

    /*
     * Free-fly: move along look (forward/back) and right (strafe), scaled by speed and dt.
     */
    public void moveRelative(float forward, float right, float dt) {
        float speed = 5.0f; // blocks per second
        // fma(s, v) does position += s*v in place.
        this.position.fma(forward * speed * dt, getLookDirection());
        this.position.fma(right * speed * dt, getRight());
    }
}
