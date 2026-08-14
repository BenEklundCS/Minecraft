package com.beneklund.minecraft.world;

import org.joml.Vector3f;

/*
 * Preetham et al. 1999, "A Practical Analytic Model for Daylight".
 *
 * Everything in here is constant for a given frame - it depends only on turbidity and where
 * the sun is, never on which way a pixel is looking. The per-pixel half of the model lives in
 * sky.frag; this class produces the handful of uniforms it needs. Splitting it this way keeps
 * the zenith cubics and that tan() off the GPU, where they'd run a couple of million times a
 * frame to produce the same answer.
 *
 * No GL in here on purpose. Fifteen hand-transcribed coefficients plus two cubics is the
 * highest-risk part of the sky, and a single wrong digit reads as "the model is broken"
 * rather than as an obvious error - so it needs to be testable, and it is.
 *
 * The model describes *daylight*. See thetaS() for what happens after sunset.
 */
public class PreethamSky {
    private static final double HALF_PI = Math.PI / 2.0;

    private float turbidity;
    private Vector3f sunDirection;

    private Vector5 luminanceY;
    private Vector5 chromaticX;
    private Vector5 chromaticY;

    public PreethamSky(float turbidity, Vector3f sunDirection) {
        this.turbidity = turbidity;
        this.sunDirection = sunDirection;
        computeCoefficients();
    }

    private record Vector5(float A, float B, float C, float D, float E) {}

    private void computeCoefficients() {
        float lya = (float) (0.1787 * turbidity) - 1.4630f;
        float lyb = (float) (-0.3554 * turbidity) + 0.4275f;
        float lyc = (float) (-0.0227 * turbidity) + 5.3251f;
        float lyd = (float) (0.1206 * turbidity) - 2.5771f;
        float lye = (float) (-0.0670 * turbidity) + 0.3703f;
        luminanceY = new Vector5(lya, lyb, lyc, lyd, lye);

        float cxa = (float) (-0.0193 * turbidity) - 0.2592f;
        float cxb = (float) (-0.0665 * turbidity) + 0.0008f;
        float cxc = (float) (-0.0004 * turbidity) + 0.2125f;
        float cxd = (float) (-0.0641 * turbidity) - 0.8989f;
        float cxe = (float) (-0.0033 * turbidity) + 0.0452f;
        chromaticX = new Vector5(cxa, cxb, cxc, cxd, cxe);

        float cya = (float) (-0.0167 * turbidity) - 0.2608f;
        float cyb = (float) (-0.0950 * turbidity) + 0.0092f;
        float cyc = (float) (-0.0079 * turbidity) + 0.2102f;
        float cyd = (float) (-0.0441 * turbidity) - 1.6537f;
        float cye = (float) (-0.0109 * turbidity) + 0.0529f;
        chromaticY = new Vector5(cya, cyb, cyc, cyd, cye);
    }

    // The sun's angle from straight up: 0 at noon, PI/2 at the horizon.
    //
    // Clamped at the horizon because Preetham is fit to daylight and has no night. Past PI/2
    // the (PI - 2*thetaS) term in zenithLuminance() goes negative, tan() flips sign, and the
    // zenith luminance comes out *negative* - which through the xyY conversion is garbage
    // colour, not darkness. Holding the sun at the horizon keeps the model inside its valid
    // domain; SkyModel.dayFactor() is what takes over from there and fades this out into night.
    public float thetaS() {
        double cosThetaS = Math.max(-1.0, Math.min(1.0, sunDirection.y));
        return (float) Math.min(Math.acos(cosThetaS), HALF_PI);
    }

    /*
     * The Perez distribution function (Perez et al. 1993). Returns a *relative* radiance -
     * it means nothing until divided by its own value at the zenith, which is what zenithF()
     * below is for.
     *
     *   cosTheta - cosine of the angle between the view ray and straight up
     *   gamma    - angle between the view ray and the sun, in radians
     *   cosGamma - cosine of that same angle
     *
     * The asymmetry is real and not a transcription slip: exp(D * gamma) takes the raw angle
     * while E * cosGamma^2 takes the cosine. That is how the function is defined.
     */
    private static float perez(float cosTheta, float gamma, float cosGamma, Vector5 c) {
        // max(), not "+ 0.01". Below the horizon cosTheta goes negative, which flips the sign
        // of B / cosTheta and sends exp() to +inf as the denominator nears zero.
        double cosT = Math.max(cosTheta, 0.01);
        return (float) ((1.0 + c.A() * Math.exp(c.B() / cosT))
                * (1.0 + c.C() * Math.exp(c.D() * gamma) + c.E() * cosGamma * cosGamma));
    }

    /*
     * F(0, thetaS) for each of the three channels - the normalisation divisor.
     *
     * All three evaluate at the zenith, so cosTheta is 1 (theta = 0, straight up) and the
     * angle to the sun from straight up is thetaS itself. Without this divisor the sky comes
     * out the right hue at wildly wrong brightness.
     */
    public Vector3f zenithF() {
        float thetaS = thetaS();
        float cosThetaS = (float) Math.cos(thetaS);
        return new Vector3f(
                perez(1.0f, thetaS, cosThetaS, luminanceY),
                perez(1.0f, thetaS, cosThetaS, chromaticX),
                perez(1.0f, thetaS, cosThetaS, chromaticY));
    }

    // Absolute values at the zenith: (Yz, xz, yz). This is where brightness and hue actually
    // come from - the Perez function only supplies the shape of the gradient.
    public Vector3f zenith() {
        return new Vector3f(zenithLuminance(), zenithX(), zenithY());
    }

    // Zenith luminance in kcd/m^2 (Preetham eq. 3). Sanity values at turbidity 2:
    // noon ~15.5, sunset ~1.99.
    //
    // tan() cannot blow up here for positive turbidity: chi peaks at (4/9 - T/120)*PI when
    // the sun is overhead, which is below PI/2 for any T > 0.
    public float zenithLuminance() {
        double chi = (4.0 / 9.0 - turbidity / 120.0) * (Math.PI - 2.0 * thetaS());
        return (float) ((4.0453 * turbidity - 4.9710) * Math.tan(chi) - 0.2155 * turbidity + 2.4192);
    }

    // Zenith chromaticities: cubics in thetaS, with T^2, T and constant terms.
    public float zenithX() {
        double t = thetaS();
        double t2 = t * t;
        double t3 = t2 * t;
        return (float) (turbidity * turbidity * (0.00166 * t3 - 0.00375 * t2 + 0.00209 * t)
                + turbidity * (-0.02903 * t3 + 0.06377 * t2 - 0.03202 * t + 0.00394)
                + (0.11693 * t3 - 0.21196 * t2 + 0.06052 * t + 0.25886));
    }

    public float zenithY() {
        double t = thetaS();
        double t2 = t * t;
        double t3 = t2 * t;
        return (float) (turbidity * turbidity * (0.00275 * t3 - 0.00610 * t2 + 0.00317 * t)
                + turbidity * (-0.04214 * t3 + 0.08970 * t2 - 0.04153 * t + 0.00516)
                + (0.15346 * t3 - 0.26756 * t2 + 0.06670 * t + 0.26688));
    }

    /*
     * The whole model evaluated on the CPU for a single view direction, returning linear RGB
     * before exposure. sky.frag does exactly this per pixel; this exists so the fog colour can
     * be sampled from the same model (GG-4 step 7) and distant terrain dissolves into the
     * colour the sky actually is in that direction, instead of into a constant that the sky
     * never contains.
     *
     * Not on the per-pixel path - one call per frame.
     */
    public Vector3f skyColor(Vector3f viewDir) {
        float cosTheta = viewDir.y;
        float cosGamma = Math.max(-1.0f, Math.min(1.0f, viewDir.dot(sunDirection)));
        float gamma = (float) Math.acos(cosGamma);

        Vector3f divisor = zenithF();
        Vector3f absolute = zenith();

        float luminance = absolute.x * perez(cosTheta, gamma, cosGamma, luminanceY) / divisor.x;
        float chromaX = absolute.y * perez(cosTheta, gamma, cosGamma, chromaticX) / divisor.y;
        float chromaY = absolute.z * perez(cosTheta, gamma, cosGamma, chromaticY) / divisor.z;

        return xyYToLinearRgb(luminance, chromaX, chromaY);
    }

    // xyY -> XYZ -> linear sRGB. Preetham's output is luminance in xyY, and skipping this
    // conversion gives a sky that is recognisably sky-shaped and wrong in hue.
    private static Vector3f xyYToLinearRgb(float bigY, float x, float y) {
        float safeY = Math.max(y, 1e-4f);
        float bigX = (x / safeY) * bigY;
        float bigZ = ((1.0f - x - safeY) / safeY) * bigY;
        return new Vector3f(
                3.2406f * bigX - 1.5372f * bigY - 0.4986f * bigZ,
                -0.9689f * bigX + 1.8758f * bigY + 0.0415f * bigZ,
                0.0557f * bigX - 0.2040f * bigY + 1.0570f * bigZ);
    }

    // The five coefficients, each packed as (luminance Y, chromatic x, chromatic y) to match
    // the vec3 uniforms sky.frag evaluates component-wise - one perez() call on the GPU
    // instead of three.
    public Vector3f coefficientA() {
        return new Vector3f(luminanceY.A(), chromaticX.A(), chromaticY.A());
    }

    public Vector3f coefficientB() {
        return new Vector3f(luminanceY.B(), chromaticX.B(), chromaticY.B());
    }

    public Vector3f coefficientC() {
        return new Vector3f(luminanceY.C(), chromaticX.C(), chromaticY.C());
    }

    public Vector3f coefficientD() {
        return new Vector3f(luminanceY.D(), chromaticX.D(), chromaticY.D());
    }

    public Vector3f coefficientE() {
        return new Vector3f(luminanceY.E(), chromaticX.E(), chromaticY.E());
    }
}
