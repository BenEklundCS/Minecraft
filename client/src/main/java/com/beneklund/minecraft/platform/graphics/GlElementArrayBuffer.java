package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15.*;

/*
 * An EBO (Element Buffer Object) - holds indices into the vertex buffer instead of
 * duplicating vertex data. If two triangles share an edge, you'd normally store those
 * two shared vertices twice in the VBO. With an EBO you store them once and reference
 * them by index.
 *
 * Works exactly like GlVertexArrayBuffer but binds to GL_ELEMENT_ARRAY_BUFFER instead.
 * At draw time you use glDrawElements instead of glDrawArrays - OpenGL walks the index
 * buffer and fetches the corresponding vertices from the VBO.
 *
 * The VAO remembers which EBO was bound when attribPointer was called, so binding the
 * VAO at draw time restores this relationship automatically.
 *
 * Lifecycle: new -> upload() once -> bind() at draw time -> delete() on shutdown.
 */
public final class GlElementArrayBuffer implements IGlBuffer {
    private final int buffer;

    public GlElementArrayBuffer() {
        buffer = glGenBuffers();
    }

    public void upload(int[] indices) {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
    }

    public void bind() {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    public void delete() {
        glDeleteBuffers(buffer);
    }
}
