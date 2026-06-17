package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.TriangleMesh;
import java.util.List;

public class HudRenderer implements IRenderable {
    private TriangleMesh triangleMesh;
    private static final ShaderProgram triangleShaderProgram =
            new ShaderProgram("/shaders/hud.vert", "/shaders/hud.frag");

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        if (triangleMesh == null) rebuildTriangleMesh();
        return List.of();
        // return List.of(new DrawCall(this.triangleMesh, null, triangleShaderProgram));
    }

    private void rebuildTriangleMesh() {
        if (triangleMesh != null) {
            triangleMesh.delete();
        }

        TriangleMesh mesh = new TriangleMesh();

        // TriangleMesh layout is 6 floats per vertex: x, y, z, r, g, b (stride 24).
        float[] vertices = {
            -1.0f, -1.0f, +0.0f, 1.0f, 0.0f, 0.0f, +1.0f, -1.0f, +0.0f, 0.0f, 1.0f, 0.0f, +0.0f, +1.0f, +0.0f, 0.0f,
            0.0f, 1.0f,
        };

        int[] indices = {0, 1, 2};

        mesh.upload(vertices, indices);
        triangleMesh = mesh;
    }
}
