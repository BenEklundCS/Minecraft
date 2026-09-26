package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

/**
 * How {@code glDrawElements} assembles indices into primitives.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Primitive">OpenGL Wiki: Primitive</a>
 */
enum PrimitiveMode {
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
