package com.beneklund.minecraft.util;

import org.joml.Math;
import org.joml.Vector3f;

public record Color(float red, float green, float blue, float alpha) {
    public static final Color SKY = new Color(0.53f, 0.81f, 0.98f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static final Color OAK_LEAF = new Color(0.475f, 0.753f, 0.353f, 1.0f);
    // Minecraft's overworld daytime fog, #C0D8FF. Still the clear color and the startup
    // fallback, but no longer what distant terrain fades to: the fog colour is now sampled
    // from the sky model per frame (Renderer.updateFogFromSky), so it matches the sky
    // it dissolves into instead of being one constant hue the sky never contains.
    public static final Color FOG = new Color(0.753f, 0.847f, 1.0f, 1.0f);
    public static final Color SKY_HORIZON = new Color(0.78f, 0.87f, 1.0f, 1.0f);
    public static final Color SKY_ZENITH = new Color(0.25f, 0.45f, 0.85f, 1.0f);

    public Vector3f toRgbVec3() {
        return new Vector3f(red, green, blue);
    }

    public static Color lerp(Color a, Color b, double t) {
        return new Color(
                (float) Math.lerp(a.red(), b.red(), t),
                (float) Math.lerp(a.green(), b.green(), t),
                (float) Math.lerp(a.blue(), b.blue(), t),
                (float) Math.lerp(a.alpha(), b.alpha(), t));
    }

    public Color scale(float factor) {
        return new Color(red * factor, green * factor, blue * factor, alpha);
    }
}
