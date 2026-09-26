package com.beneklund.minecraft.world.sky;

import org.joml.Vector3f;

/**
 * Time of day as a fraction of a day in {@code [0, 1)}, and the sun and ambient brightness
 * derived from it.
 *
 * <p>0 is midnight, 0.25 sunrise, 0.5 noon, 0.75 sunset. The sun travels a great circle through
 * the zenith in the y-z plane, rising at +z and setting at -z.
 */
public class DayNightCycle {
    private static final float FULL_DAY = 1.0f;
    private static final double CURVE_MIDPOINT = 0.5f;
    private static final double CURVE_AMPLITUDE = 0.5f;
    private static final double FULL_CYCLE_RADIANS = Math.PI * 2;

    public static final float MORNING = 0.25f;
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

    /**
     * @param timeOfDay starting fraction of a day, such as {@link #MORNING}
     * @param dayLengthSeconds real seconds per full day
     */
    public DayNightCycle(float timeOfDay, float dayLengthSeconds) {
        this.timeOfDay = timeOfDay;
        this.dayLengthSeconds = dayLengthSeconds;
    }

    /** Advances by {@code dt} real seconds, wrapping at midnight. */
    public void advance(float dt) {
        timeOfDay = (timeOfDay + dt / dayLengthSeconds) % FULL_DAY;
    }

    /**
     * Ambient brightness from {@link #NIGHT_BRIGHTNESS} at midnight to {@link #DAY_BRIGHTNESS} at
     * noon, along a cosine. The floor stays above zero so caves and terrain remain readable at
     * night.
     */
    public float skyBrightness() {
        double curve = CURVE_MIDPOINT - (CURVE_AMPLITUDE * Math.cos(FULL_CYCLE_RADIANS * timeOfDay));
        return (float) (NIGHT_BRIGHTNESS + ((double) DAY_BRIGHTNESS - NIGHT_BRIGHTNESS) * curve);
    }

    /** Unit vector toward the sun. Its {@code y} is the sine of the sun's altitude. */
    public Vector3f sunDirection() {
        double angle = (timeOfDay - 0.25f) * FULL_CYCLE_RADIANS;
        return new Vector3f(0.0f, (float) Math.sin(angle), (float) Math.cos(angle)).normalize();
    }

    /** Sets the time, wrapping any value, negative included, into {@code [0, 1)}. */
    public void setTimeOfDay(float t) {
        timeOfDay = ((t % 1.0f) + 1.0f) % 1.0f;
    }

    public float timeOfDay() {
        return timeOfDay;
    }
}
