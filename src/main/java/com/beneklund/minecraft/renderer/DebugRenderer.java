package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.LineMesh;
import com.beneklund.minecraft.util.RaycastResult;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class DebugRenderer implements IRenderable {
    private static final String VERT_PATH = "/shaders/debug.vert";
    private static final String FRAG_PATH = "/shaders/debug.frag";
    private static final float REACH = 8.0f;
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final ShaderProgram debugShader;

    private LineMesh laserMesh;
    private LineMesh targetMesh;
    private Matrix4f targetTransform;
    private boolean laserDirty = true;
    private Vector3f laserStart;
    private Vector3f laserEnd;

    public DebugRenderer() {
        debugShader = new ShaderProgram(VERT_PATH, FRAG_PATH);
    }

    public void setLaser(Vector3f start, Vector3f end) {
        laserStart = new Vector3f(start);
        laserEnd = new Vector3f(end);
        laserDirty = true;
    }

    public void clearLaser() {
        laserStart = null;
        laserEnd = null;
        laserDirty = true;
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

    public void updateTargetedBlock(RaycastResult result) {
        if (result.hit()) {
            var b = result.blockPos();
            targetTransform = new Matrix4f().translation(b.x, b.y, b.z);
        } else {
            targetTransform = null;
        }
    }

    /*
      7--------6
     /|       /|     top face (y=1):    4,5,6,7
    4--------5 |     bottom face (y=0):  0,1,2,3
    | 3------|-2
    |/       |/
    0--------1
      */
    private void rebuildTargetMesh() {
        float lo = -0.002f, hi = 1.002f;
        if (targetMesh != null) {
            targetMesh.delete();
            targetMesh = null;
        }
        float[] vertices = {
            lo, lo, lo, 1f, 0f, 0f, // 0
            hi, lo, lo, 1f, 0f, 0f, // 1
            hi, lo, hi, 1f, 0f, 0f, // 2
            lo, lo, hi, 1f, 0f, 0f, // 3
            lo, hi, lo, 1f, 0f, 0f, // 4
            hi, hi, lo, 1f, 0f, 0f, // 5
            hi, hi, hi, 1f, 0f, 0f, // 6
            lo, hi, hi, 1f, 0f, 0f, // 7
        };
        int[] indices = {
            0, 1, 1, 2, 2, 3, 3, 0, // bottom square
            4, 5, 5, 6, 6, 7, 7, 4, // top square
            0, 4, 1, 5, 2, 6, 3, 7, // verticals
        };
        targetMesh = new LineMesh();
        targetMesh.upload(vertices, indices);
    }

    private void rebuildLaserMesh() {
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
        if (laserDirty) rebuildLaserMesh();
        if (targetMesh == null) rebuildTargetMesh();
        List<DrawCall> calls = new ArrayList<>();
        if (laserMesh != null) {
            calls.add(new DrawCall(laserMesh, IDENTITY, debugShader));
        }
        if (targetTransform != null) {
            calls.add(new DrawCall(targetMesh, targetTransform, debugShader));
        }
        return calls;
    }

    @Override
    public void reload() {
        debugShader.reload();
    }

    @Override
    public void delete() {
        debugShader.delete();
        if (laserMesh != null) laserMesh.delete();
    }
}
