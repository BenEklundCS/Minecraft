package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

public enum PrimitiveMode {
    TRIANGLES(GL_TRIANGLES),
    LINES(GL_LINES);

    private final int mode;

    PrimitiveMode(int mode) {
        this.mode = mode;
    }

    public int mode() {
        return mode;
    }
}
