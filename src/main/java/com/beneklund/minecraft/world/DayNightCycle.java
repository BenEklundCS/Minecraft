package com.beneklund.minecraft.world;

public class DayNightCycle {
    public static final float NOON = 0.5f;
    public static final float MIDNIGHT = 0.0f;
    private static final float NIGHT_BRIGHTNESS = 0.15f;
    private float timeOfDay;

    public DayNightCycle(float timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public void advance(float dt) {}

    public float skyBrightness() {
        double curve = 0.5 - 0.5 * Math.cos(Math.PI * 2 * timeOfDay);
        return (float) (NIGHT_BRIGHTNESS + (1.0 - NIGHT_BRIGHTNESS) * curve);
    }

    public float timeOfDay() {
        return timeOfDay;
    }
}
