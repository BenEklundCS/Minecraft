package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.*;

public abstract class Mesh {
    private final PrimitiveMode primitive;

    private final GlVertexArray vao;
    private final GlVertexArrayBuffer vbo;
    private final GlElementArrayBuffer ebo;

    private final int indexCount;
    private final int vertexCount;

    public Mesh(Geometry geometry, VertexFormat vf, PrimitiveMode primitive) {
        vf.checkVertexCount(geometry.vertices().length);
        this.primitive = primitive;

        vao = new GlVertexArray();
        vbo = new GlVertexArrayBuffer();
        ebo = new GlElementArrayBuffer();

        vao.bind();
        vbo.upload(geometry.vertices());
        ebo.upload(geometry.indices());
        vf.describe(vao);
        vao.unbind();
        indexCount = geometry.indices().length;
        vertexCount = geometry.vertices().length / vf.floatsPerVertex();
    }

    public int vertexCount() {
        return vertexCount;
    }

    public void render() {
        if (indexCount == 0) return;
        vao.bind();
        glDrawElements(primitive.mode(), indexCount, GL_UNSIGNED_INT, 0L);
    }

    public void delete() {
        vao.delete();
        vbo.delete();
        ebo.delete();
    }
}
