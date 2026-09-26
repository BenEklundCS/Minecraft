package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.*;

/**
 * Indexed geometry resident on the GPU: a {@link GlVertexArray} owning one VBO and one EBO,
 * drawn with {@code glDrawElements}.
 *
 * <p>The constructor uploads {@link Geometry} and records the attribute layout from a {@link
 * VertexFormat}, rejecting a vertex array whose length isn't a whole number of vertices. Each
 * subclass fixes the format and primitive for one kind of draw. Construction and {@link #delete()}
 * are GL calls and belong on the main thread.
 *
 * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glDrawElements.xhtml">
 *     glDrawElements</a>
 */
public abstract class Mesh {
    private final PrimitiveMode primitive;

    private final GlVertexArray vao;
    private final GlVertexArrayBuffer vbo;
    private final GlElementArrayBuffer ebo;

    private final int indexCount;
    private final int vertexCount;

    /** @throws IllegalArgumentException if the vertex data doesn't divide into whole vertices */
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

    /** Draws the whole mesh with whatever program is bound. An empty mesh draws nothing. */
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
