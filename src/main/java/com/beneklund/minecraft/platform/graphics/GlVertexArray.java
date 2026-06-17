package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.*;

/*
 * A VAO (Vertex Array Object) - remembers how to interpret the data in a VBO.
 *
 * Uploading vertex data to a VBO gives the GPU a flat stream of bytes. The GPU has
 * no idea what any of it means. attribPointer() is how you tell it: "attribute slot 0
 * starts at byte 0, is 3 floats wide, and vertices are 20 bytes apart." The VAO
 * records that description so you don't have to repeat it every frame.
 *
 * The order of calls during setup matters. The VAO records which VBO was bound at
 * the moment attribPointer() was called - that's how it knows which buffer feeds
 * which attribute slot. Bind the VAO first, then upload/bind the VBO, then call
 * attribPointer.
 *
 * At draw time, binding the VAO restores the entire attribute layout and the VBO
 * association in one call.
 *
 * Lifecycle: new -> bind() -> attribPointer() per attribute -> unbind() -> bind() each frame -> delete() on shutdown.
 */
public class GlVertexArray {
    private final int vertexArray;

    public GlVertexArray() {
        vertexArray = glGenVertexArrays();
    }

    public void bind() {
        glBindVertexArray(vertexArray);
    }

    public void unbind() {
        // Bind 0 so subsequent GL calls don't accidentally modify this VAO's state.
        glBindVertexArray(0);
    }

    public void delete() {
        glDeleteVertexArrays(vertexArray);
    }

    public void attribPointer(int index, int size, int stride, long offset) {
        // Must be called while this VAO is bound - the VAO records this description.
        // index = attribute slot (matches layout(location = N) in the vertex shader)
        // size = number of floats for this attribute
        // stride = total bytes per vertex across all attributes
        // offset = byte offset to where this attribute starts within a vertex
        glVertexAttribPointer(index, size, GL_FLOAT, false, stride, offset);
        glEnableVertexAttribArray(index);
    }
}
