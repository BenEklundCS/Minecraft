package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.LineMesh;
import com.beneklund.minecraft.util.RaycastResult;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class DebugRenderer implements IRenderable {
    private static final float REACH = 8.0f;
    private static final ShaderProgram DEBUG_SHADER = new ShaderProgram("/shaders/debug.vert", "/shaders/debug.frag");
    private static final Matrix4f IDENTITY = new Matrix4f();

    private LineMesh laserMesh;
    private boolean laserDirty = true;
    private Vector3f laserStart;
    private Vector3f laserEnd;

    public void setLaser(Vector3f start, Vector3f end) {
        this.laserStart = new Vector3f(start);
        this.laserEnd = new Vector3f(end);
        this.laserDirty = true;
    }

    public void clearLaser() {
        this.laserStart = null;
        this.laserEnd = null;
        this.laserDirty = true;
    }

    public void updateFromRaycast(Vector3f origin, Vector3f direction, RaycastResult result) {
        Vector3f end = result.hit()
                ? new Vector3f(origin)
                        .add(
                                direction.x * result.distance(),
                                direction.y * result.distance(),
                                direction.z * result.distance())
                : new Vector3f(origin).add(direction.x * REACH, direction.y * REACH, direction.z * REACH);
        setLaser(origin, end);
    }

    private void rebuildMesh() {
        if (laserMesh != null) {
            laserMesh.delete();
            laserMesh = null;
        }
        if (laserStart == null || laserEnd == null) {
            laserDirty = false;
            return;
        }
        float[] vertices = {
            laserStart.x, laserStart.y, laserStart.z, 1.0f, 0.0f, 0.0f,
            laserEnd.x, laserEnd.y, laserEnd.z, 1.0f, 0.0f, 0.0f,
        };
        int[] indices = {0, 1};
        laserMesh = new LineMesh();
        laserMesh.upload(vertices, indices);
        laserDirty = false;
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        if (laserDirty) rebuildMesh();
        if (laserMesh == null) return List.of();
        return List.of(new DrawCall(laserMesh, IDENTITY, DEBUG_SHADER));
    }

    @Override
    public void delete() {
        DEBUG_SHADER.delete();
        if (laserMesh != null) laserMesh.delete();
    }
}
