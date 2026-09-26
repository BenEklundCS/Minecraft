package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.*;

/**
 * A vertex array object (VAO): the recorded description of how vertex buffers feed a vertex
 * shader's inputs.
 *
 * <p>A VBO is untyped bytes. Each {@link #attribPointer} call records, into the bound VAO, one
 * attribute slot's component count, type, stride and byte offset, plus the buffer bound to {@code
 * GL_ARRAY_BUFFER} at that moment. The VAO also records the {@code GL_ELEMENT_ARRAY_BUFFER}
 * binding. Binding the VAO at draw time restores all of it in one call.
 *
 * <p>Setup order: {@link #bind()}, bind or upload the VBO and EBO, {@link #attribPointer} once per
 * attribute, {@link #unbind()}. {@link Mesh} does exactly this through {@link
 * VertexFormat#describe}.
 *
 * <p>The core profile has no default VAO, so every draw needs one bound.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Vertex_Specification">OpenGL Wiki: Vertex
 *     Specification</a>
 * @see <a href="https://learnopengl.com/Getting-started/Hello-Triangle">LearnOpenGL: Hello
 *     Triangle</a>
 */
final class GlVertexArray {
    private final int vertexArray;

    public GlVertexArray() {
        vertexArray = glGenVertexArrays();
    }

    public void bind() {
        glBindVertexArray(vertexArray);
    }

    /** Binds VAO 0 so later buffer and attribute calls can't write into this one. */
    public void unbind() {
        glBindVertexArray(0);
    }

    public void delete() {
        glDeleteVertexArrays(vertexArray);
    }

    /**
     * Records one attribute's layout into this VAO and enables it. This VAO must be bound, and the
     * VBO holding the attribute must be bound to {@code GL_ARRAY_BUFFER}.
     *
     * @param index attribute slot, matching {@code layout(location = N)} in the vertex shader
     * @param size component count, 1 to 4
     * @param glType component type, e.g. {@code GL_FLOAT}
     * @param normalized whether integer components map to [0, 1] or [-1, 1]
     * @param stride bytes from one vertex to the next
     * @param offset byte offset of this attribute within a vertex
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glVertexAttribPointer.xhtml">
     *     glVertexAttribPointer</a>
     */
    public void attribPointer(int index, int size, int glType, boolean normalized, int stride, long offset) {
        glVertexAttribPointer(index, size, glType, normalized, stride, offset);
        glEnableVertexAttribArray(index);
    }
}
