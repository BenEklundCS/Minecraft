package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15.*;

/*
 * A VBO (Vertex Buffer Object) - a named chunk of GPU memory that holds vertex data.
 *
 * OpenGL is a state machine. glBufferData doesn't take a buffer ID - it operates on
 * whatever is currently bound to GL_ARRAY_BUFFER. So upload() binds first, then sends
 * the data.
 *
 * GL_STATIC_DRAW is a hint to the GPU about how this memory will be used - written
 * once, read many times. The driver uses this to decide where to put the memory.
 *
 * Lifecycle: new -> upload() once -> bind() at draw time -> delete() on shutdown.
 */
public class GlVertexArrayBuffer implements GlBuffer {
    private final int buffer;

    public GlVertexArrayBuffer() {
        this.buffer = glGenBuffers();
    }

    public void upload(float[] vertices) {
        glBindBuffer(GL_ARRAY_BUFFER, this.buffer);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
    }

    public void bind() {
        glBindBuffer(GL_ARRAY_BUFFER, this.buffer);
    }

    public void delete() {
        glDeleteBuffers(this.buffer);
    }
}
