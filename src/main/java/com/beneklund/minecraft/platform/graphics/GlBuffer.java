package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15.*;

public class GlBuffer {
    public enum Target {
        ARRAY(GL_ARRAY_BUFFER),
        ELEMENT(GL_ELEMENT_ARRAY_BUFFER);

        final int glTarget;
        Target(int glTarget) { this.glTarget = glTarget; }
    }

    private int buffer;

    public GlBuffer() {
        genBuffer();
    }

    private void genBuffer() {
        this.buffer = glGenBuffers();
    }

    public void upload(float[] vertices, Target target) {
        glBindBuffer(target.glTarget, this.buffer);
        glBufferData(target.glTarget, vertices, GL_STATIC_DRAW);
    }

    public void upload(int[] indices, Target target) {
        glBindBuffer(target.glTarget, this.buffer);
        glBufferData(target.glTarget, indices, GL_STATIC_DRAW);
    }

    public void bind(Target target) {
        glBindBuffer(target.glTarget, this.buffer);
    }

    public void delete() {
        glDeleteBuffers(this.buffer);
    }
}
