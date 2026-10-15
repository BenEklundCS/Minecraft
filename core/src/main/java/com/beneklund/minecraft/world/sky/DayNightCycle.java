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
    public record CycleState(float time, DayLength duration) {}
    ;

    public enum DayLength {
        SHORT(600f),
        VSHORT(300f),
        DEFAULT(1200f),
        LONG(1800f);

        final float seconds;

        DayLength(float seconds) {
            this.seconds = seconds;
        }

        public float getSeconds() {
            return seconds;
        }

        public static DayLength ofSeconds(float seconds) {
            for (DayLength d : values()) {
                if (d.seconds == seconds) return d;
            }
            throw new IllegalArgumentException(String.format("No day length of %f seconds", seconds));
        }
    }

    private static final float FULL_DAY = 1.0f;
    private static final double CURVE_MIDPOINT = 0.5f;
    private static final double CURVE_AMPLITUDE = 0.5f;
    private static final double FULL_CYCLE_RADIANS = Math.PI * 2;

    public static final float MORNING = 0.25f;
    public static final float NOON = 0.5f;
    public static final float MIDNIGHT = 0.0f;
    public static final float NIGHT_BRIGHTNESS = 0.15f;
    public static final float DAY_BRIGHTNESS = 1.0f;

    private float timeOfDay;
    private DayLength dayLength;

    /**
     * @param timeOfDay - the time of day
     * @param dayLength - the length of the day
     */
    public DayNightCycle(float timeOfDay, DayLength dayLength) {
        this.timeOfDay = timeOfDay;
        this.dayLength = dayLength;
    }

    /**
     * @param state - construct a DayNightCycle from a CycleState
     */
    public DayNightCycle(CycleState state) {
        timeOfDay = state.time();
        dayLength = state.duration();
    }

    /** Advances by {@code dt} real seconds, wrapping at midnight. */
    public void advance(float dt) {
        timeOfDay = (timeOfDay + dt / dayLength.seconds) % FULL_DAY;
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

    public void setDayLength(DayLength dayLength) {
        this.dayLength = dayLength;
    }

    public DayLength getDayLengthSeconds() {
        return dayLength;
    }

    public void apply(CycleState state) {
        timeOfDay = state.time();
        dayLength = state.duration();
    }

    public CycleState getCycleState() {
        return new CycleState(timeOfDay, dayLength);
    }
}
