package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

public class LineMesh implements Mesh {
    private static final VertexFormat vf = VertexFormat.LINE;
    private final GlVertexArray vao;
    private final IGlVertexArrayBuffer vbo;
    private final IGlElementArrayBuffer ebo;
    private int indexCount;

    public LineMesh() {
        vao = new GlVertexArray();
        vbo = new IGlVertexArrayBuffer();
        ebo = new IGlElementArrayBuffer();
        indexCount = 0;
    }

    // Vertex format: 6 floats per vertex — x, y, z, r, g, b. Stride = 24 bytes.
    public void upload(float[] vertices, int[] indices) {
        vao.bind();
        vbo.upload(vertices);
        ebo.upload(indices);
        vf.describe(vao);
        vao.unbind();
        indexCount = indices.length;
    }

    public void render() {
        if (indexCount == 0) return;
        vao.bind();
        glDrawElements(GL_LINES, indexCount, GL_UNSIGNED_INT, 0L);
    }

    public void delete() {
        vao.delete();
        vbo.delete();
        ebo.delete();
    }
}
