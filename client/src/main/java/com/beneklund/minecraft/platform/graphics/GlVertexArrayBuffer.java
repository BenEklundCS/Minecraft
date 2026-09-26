package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15.*;

/**
 * A vertex buffer object (VBO): GPU memory holding interleaved vertex data.
 *
 * <p>{@code glBufferData} takes a binding target, not a buffer name, and writes to whatever is
 * bound there, so {@link #upload} binds to {@code GL_ARRAY_BUFFER} first. {@code GL_STATIC_DRAW}
 * tells the driver the data is written once and drawn many times, which lets it place the buffer
 * in video memory.
 *
 * <p>Lifecycle: construct, {@link #upload} once, draw through a {@link GlVertexArray}, {@link
 * #delete()}.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Buffer_Object">OpenGL Wiki: Buffer Object</a>
 * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBufferData.xhtml">
 *     glBufferData</a>
 */
public final class GlVertexArrayBuffer implements IGlBuffer {
    private final int buffer;

    public GlVertexArrayBuffer() {
        buffer = glGenBuffers();
    }

    /** Replaces the buffer's contents, reallocating its storage to fit. */
    public void upload(float[] vertices) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
    }

    public void bind() {
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
    }

    public void delete() {
        glDeleteBuffers(buffer);
    }
}
