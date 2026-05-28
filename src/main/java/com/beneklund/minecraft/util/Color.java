package com.beneklund.minecraft.util;

public record Color(float red, float green, float blue, float alpha) {
    public static final Color SKY = new Color(0.53f, 0.81f, 0.98f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
}
