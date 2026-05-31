package com.beneklund.minecraft.util;

public record Color(float red, float green, float blue, float alpha) {
    public static final Color SKY = new Color(0.53f, 0.81f, 0.98f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static final Color OAK_LEAF = new Color(0.475f, 0.753f, 0.353f, 1.0f);

    // RGB-only array for use in vertex buffers where alpha isn't part of the format.
    public float[] toRgbArray() {
        return new float[] {red, green, blue};
    }
}
