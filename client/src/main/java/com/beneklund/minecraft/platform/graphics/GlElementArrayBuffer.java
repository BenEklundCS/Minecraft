package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15.*;

/**
 * An element buffer object (EBO): indices into a VBO, so vertices shared between triangles are
 * stored once.
 *
 * <p>A quad is four vertices and six indices instead of six vertices. {@code glDrawElements}
 * walks this buffer and fetches each referenced vertex from the VBO.
 *
 * <p>The {@code GL_ELEMENT_ARRAY_BUFFER} binding is VAO state: binding this while a {@link
 * GlVertexArray} is bound attaches it to that VAO, and binding the VAO later restores it.
 *
 * <p>Lifecycle: construct, {@link #upload} once while the owning VAO is bound, {@link #delete()}.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Vertex_Specification#Index_buffers">OpenGL
 *     Wiki: Index buffers</a>
 * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glDrawElements.xhtml">
 *     glDrawElements</a>
 */
public final class GlElementArrayBuffer implements IGlBuffer {
    private final int buffer;

    public GlElementArrayBuffer() {
        buffer = glGenBuffers();
    }

    /** Replaces the index data. Binds the buffer, so the owning VAO must already be bound. */
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
