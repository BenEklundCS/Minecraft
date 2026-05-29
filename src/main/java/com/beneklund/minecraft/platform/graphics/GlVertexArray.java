package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.*;

public class GlVertexArray {
    private int vertexArray;

    public GlVertexArray() {
        genVertexArray();
    }

    private void genVertexArray() {
        this.vertexArray = glGenVertexArrays();
    }

    public void bind() {
        glBindVertexArray(this.vertexArray);
    }

    public void unbind() {
        glBindVertexArray(0);
    }

    public void delete() {
        glDeleteVertexArrays(this.vertexArray);
    }

    public void attribPointer(int index, int size, int stride, long offset) {
        glVertexAttribPointer(index, size, GL_FLOAT, false, stride, offset);
        glEnableVertexAttribArray(index);
    }
}
