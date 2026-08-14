package com.beneklund.minecraft.world;

import org.joml.Vector3f;

/*
 * Preetham is a daylight model - thetaS is clamped at the horizon, so below it the sky would
 * sit at sunset brightness forever. Measured at turbidity 2.5, zenith luminance stops falling
 * at 1.88 kcd/m^2 the moment the sun touches the horizon and stays there until dawn, while the
 * Perez sun lobe keeps tracking a sun that is now underground - a warm band parked on the
 * horizon, swinging around all night. Real night sky is four orders of magnitude dimmer.
 *
 * So this fades the model out over civil twilight and hands off to a plain night gradient.
 * Nothing here is Preetham; PreethamSky stays a faithful transcription of the paper, and the
 * "what happens after the sun sets" decision lives out here where it can be tuned.
 *
 * sky.frag does the same blend per pixel. It gets uDayFactor and both night colours as
 * uniforms from this class rather than declaring its own consts, because the fog colour is
 * sampled from colorFor() on the CPU and the two have to agree exactly - the horizon is where
 * fog meets sky, and any disagreement shows up as a seam.
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

    // The daylight half, for the coefficient uniforms sky.frag evaluates per pixel.
    public PreethamSky preetham() {
        return sky;
    }

    // 1 while the sun is up, 0 once it is well down, smooth across twilight.
    public float dayFactor() {
        return smoothstep(NIGHT_BELOW, DAY_ABOVE, sunDirection.y);
    }

    // Copies. Renderer hands these straight to the uniform map, which lives next to a
    // fogColorVec it mutates in place every frame - one set() on the wrong vector would
    // corrupt the constant for the rest of the process.
    public Vector3f nightHorizon() {
        return new Vector3f(NIGHT_HORIZON);
    }

    public Vector3f nightZenith() {
        return new Vector3f(NIGHT_ZENITH);
    }

    /*
     * The final on-screen sky colour for one direction: Preetham, exposed, then blended into
     * night. This is what sky.frag computes per pixel, minus the sun disc - fog should take the
     * colour of the sky around the sun, not of the sun itself.
     *
     * One call per frame (Renderer.updateFogFromSky), not a per-pixel path.
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

    // Same exponential curve as sky.frag: radiance is unbounded, the framebuffer is not.
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
