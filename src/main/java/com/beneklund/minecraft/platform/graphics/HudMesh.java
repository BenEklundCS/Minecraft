package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.*;

public class HudMesh implements Mesh {
    private static final VertexFormat vf = VertexFormat.HUD;
    private final GlVertexArray vao;
    private final IGlVertexArrayBuffer vbo;
    private final IGlElementArrayBuffer ebo;
    private int indexCount;

    public HudMesh() {
        vao = new GlVertexArray();
        vbo = new IGlVertexArrayBuffer();
        ebo = new IGlElementArrayBuffer();
        indexCount = 0;
    }

    // Vertex format: 8 floats per vertex - x, y, r, g, b, a, u, v. Stride = 32 bytes (4 bytes * 8 floats)
    public void upload(float[] vertices, int[] indices) {
        vao.bind();
        vbo.upload(vertices);
        ebo.upload(indices);
        vf.describe(vao);
        vao.unbind();
        indexCount = indices.length;
    }

    @Override
    public void render() {
        if (indexCount == 0) return;
        vao.bind();
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
    }

    @Override
    public void delete() {
        vao.delete();
        vbo.delete();
        ebo.delete();
    }
}
