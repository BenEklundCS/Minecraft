package com.beneklund.minecraft.world.sky;

import org.joml.Vector3f;

/**
 * The sky across a whole day: {@link PreethamSky} while the sun is up, faded into a fixed night
 * gradient over civil twilight.
 *
 * <p>Preetham models daylight only. {@link PreethamSky#thetaS()} holds the sun at the horizon
 * once it sets, so on its own the sky would stay at sunset brightness all night: measured at
 * turbidity 2.5, zenith luminance stops at 1.88 kcd/m^2, and the Perez sun lobe keeps tracking a
 * sun that is below the ground, leaving a warm band swinging along the horizon. A real night sky is
 * about four orders of magnitude dimmer. {@link #dayFactor()} blends from the model to the night
 * gradient as the sun drops from 0 to about -6 degrees. This class owns that decision, so
 * {@code PreethamSky} stays a faithful transcription of the paper.
 *
 * <p>The blend itself runs in GLSL: {@code sky.frag} and the distance haze in {@code chunk.frag}
 * both mix night and day with {@code uDayFactor}, {@code uNightHorizon} and {@code uNightZenith}
 * from this class. Sharing one source keeps the haze and the sky identical where they meet at the
 * horizon.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Twilight#Civil_twilight">Civil twilight</a>
 * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/smoothstep.xhtml">GLSL
 *     smoothstep</a>
 */
public class SkyModel {
    // Sun altitude (sin of it, which is just sunDirection.y) where the handoff happens.
    // Civil twilight is 0 to -6 degrees - it starts when the sun *reaches* the horizon, not
    // before. DAY_ABOVE at +0.05 (about +3 degrees) put the sky at 74% day with the sun still
    // visibly up, which reads as dusk arriving early, hardest to miss from high ground where
    // there is a lot of sky in frame.
    private static final float NIGHT_BELOW = -0.10f;
    private static final float DAY_ABOVE = 0.0f;

    // Post-exposure, so these are the colours as they land on screen, not radiances.
    private static final Vector3f NIGHT_HORIZON = new Vector3f(0.012f, 0.018f, 0.035f);
    private static final Vector3f NIGHT_ZENITH = new Vector3f(0.002f, 0.004f, 0.010f);

    private final float turbidity;
    private final float exposure;

    private PreethamSky sky;
    private Vector3f sunDirection;

    public SkyModel(float turbidity, float exposure, Vector3f sunDirection) {
        this.turbidity = turbidity;
        this.exposure = exposure;
        setSunDirection(sunDirection);
    }

    public void setSunDirection(Vector3f sunDirection) {
        this.sunDirection = sunDirection;
        sky = new PreethamSky(turbidity, sunDirection);
    }

    /** The daylight model for the current sun, source of the Perez coefficient uniforms. */
    public PreethamSky preetham() {
        return sky;
    }

    /** 1 with the sun above the horizon, 0 once its altitude sine is below -0.1, smoothstep between. */
    public float dayFactor() {
        return smoothstep(NIGHT_BELOW, DAY_ABOVE, sunDirection.y);
    }

    /**
     * A copy of the night colour at the horizon. Copies because the caller puts them straight into
     * a mutable uniform map, and a write through a shared constant would corrupt it for the rest of
     * the process.
     */
    public Vector3f nightHorizon() {
        return new Vector3f(NIGHT_HORIZON);
    }

    /** A copy of the night colour straight up; see {@link #nightHorizon()}. */
    public Vector3f nightZenith() {
        return new Vector3f(NIGHT_ZENITH);
    }

    /**
     * The sky colour in one direction, evaluated on the CPU: Preetham, exposed with {@code 1 -
     * exp(-exposure * L)}, then blended into the night gradient. Excludes the sun disc. {@code
     * SkyModelTest} pins it; it has no caller in the renderer.
     */
    public Vector3f colorFor(Vector3f viewDir) {
        Vector3f linear = sky.skyColor(viewDir);
        float day = dayFactor();
        float up = clamp01(viewDir.y);
        return new Vector3f(
                mix(mix(NIGHT_HORIZON.x, NIGHT_ZENITH.x, up), exposed(linear.x), day),
                mix(mix(NIGHT_HORIZON.y, NIGHT_ZENITH.y, up), exposed(linear.y), day),
                mix(mix(NIGHT_HORIZON.z, NIGHT_ZENITH.z, up), exposed(linear.z), day));
    }

    private float exposed(float linear) {
        return (float) (1.0 - Math.exp(-exposure * Math.max(linear, 0.0f)));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.min(1.0f, Math.max(0.0f, v));
    }

    // GLSL smoothstep, so the CPU and the shader agree on the shape of the fade.
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }
}
