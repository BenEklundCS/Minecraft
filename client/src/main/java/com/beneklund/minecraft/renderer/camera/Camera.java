package com.beneklund.minecraft.renderer.camera;

import com.beneklund.minecraft.container.CameraConfig;
import com.beneklund.minecraft.container.WindowConfig;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Builds the view and projection matrices from an eye position and look direction that {@code
 * Player} pushes in each frame. Holds no simulation state.
 *
 * <p>The view matrix is a JOML {@code lookAt} with world +Y as up. The projection is a right-handed
 * OpenGL perspective with depth mapped to [-1, 1], vertical FOV in degrees, and the aspect ratio
 * recomputed on every call so a resize takes effect immediately. Every getter allocates a new
 * matrix.
 *
 * @see <a href="https://learnopengl.com/Getting-started/Camera">LearnOpenGL: Camera</a>
 * @see <a href="https://learnopengl.com/Getting-started/Coordinate-Systems">LearnOpenGL: Coordinate
 *     Systems</a>
 * @see <a href="https://www.songho.ca/opengl/gl_projectionmatrix.html">Song Ho Ahn: OpenGL
 *     Projection Matrix</a>
 */
public class Camera {
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 1000.0f;

    private final Vector2f windowSize;
    private final Vector3f position = new Vector3f();
    private final Vector3f front = new Vector3f(0, 0, 1);
    private float fov;

    public Camera(WindowConfig config, CameraConfig cameraConfig) {
        windowSize = new Vector2f(config.width(), config.height());
        fov = cameraConfig.fov();
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setFront(Vector3f front) {
        this.front.set(front);
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f()
                .perspective((float) Math.toRadians(fov), windowSize.x / windowSize.y, NEAR_PLANE, FAR_PLANE);
    }

    /** Projection times view: world space to clip space. */
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
        windowSize.set(width, height);
    }

    public Vector2f getWindowSize() {
        return windowSize;
    }

    /**
     * The eye position the view matrix was built from. {@code chunk.frag} uses its height to work
     * out how much air a view ray passes through, which is cheaper than a world-space position
     * varying on every vertex.
     */
    public Vector3f getPosition() {
        return position;
    }
}
