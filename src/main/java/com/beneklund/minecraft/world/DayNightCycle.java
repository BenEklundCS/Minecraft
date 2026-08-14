package com.beneklund.minecraft.world;

public class DayNightCycle {
    private static final float FULL_DAY = 1.0f;
    private static final double CURVE_MIDPOINT = 0.5f;
    private static final double CURVE_AMPLITUDE = 0.5f;
    private static final double FULL_CYCLE_RADIANS = Math.PI * 2;

    public static final float NOON = 0.5f;
    public static final float MIDNIGHT = 0.0f;
    public static final float NIGHT_BRIGHTNESS = 0.15f;
    public static final float DAY_BRIGHTNESS = 1.0f;

    public static final float SHORT_DAY_SECONDS = 600f;
    public static final float VERY_SHORT_DAY_SECONDS = SHORT_DAY_SECONDS / 2;
    public static final float DEFAULT_DAY_SECONDS = SHORT_DAY_SECONDS * 2;
    public static final float LONG_DAY_SECONDS = SHORT_DAY_SECONDS * 3;

    private float timeOfDay;
    private final float dayLengthSeconds;

    public DayNightCycle(float timeOfDay, float dayLengthSeconds) {
        this.timeOfDay = timeOfDay;
        this.dayLengthSeconds = dayLengthSeconds;
    }

    public void advance(float dt) {
        timeOfDay = (timeOfDay + dt / dayLengthSeconds) % FULL_DAY;
    }

    public float skyBrightness() {
        double curve = CURVE_MIDPOINT - (CURVE_AMPLITUDE * Math.cos(FULL_CYCLE_RADIANS * timeOfDay));
        return (float) (NIGHT_BRIGHTNESS + ((double) DAY_BRIGHTNESS - NIGHT_BRIGHTNESS) * curve);
    }

    public float timeOfDay() {
        return timeOfDay;
    }
}
