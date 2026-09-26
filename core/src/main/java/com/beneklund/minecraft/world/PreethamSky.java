package com.beneklund.minecraft.world;

import org.joml.Vector3f;

/**
 * The Preetham, Shirley and Smits (1999) analytic daylight model: sky colour from turbidity and
 * sun position, for the parts that are constant across a frame.
 *
 * <p>The model has two halves. The zenith values ({@link #zenith()}) set absolute brightness and
 * hue straight overhead. The Perez distribution (Perez, Seals and Michalsky, 1993) sets the shape
 * of the gradient away from the zenith, with five coefficients A to E per channel, each linear in
 * turbidity. Sky colour in a direction is {@code zenith * F(theta, gamma) / F(0, thetaS)}, computed
 * separately for luminance Y and chromaticities x and y, then converted from CIE xyY to linear
 * sRGB.
 *
 * <p>Everything here depends only on turbidity and the sun, never on the view direction, so this
 * class computes it once per frame and hands {@code sky.frag} a handful of uniforms. The per-pixel
 * Perez evaluation runs on the GPU; the zenith cubics and the {@code tan()} stay off it.
 *
 * <p>No GL, so it is unit-testable. Fifteen hand-transcribed coefficients and two cubics are the
 * riskiest part of the sky, and one wrong digit produces a plausible but wrong sky with no obvious
 * error.
 *
 * <p>The model covers daylight only; see {@link #thetaS()} and {@link SkyModel} for night.
 *
 * @see <a href="https://courses.cs.duke.edu/cps124/fall01/resources/p91-preetham.pdf">Preetham,
 *     Shirley, Smits: A Practical Analytic Model for Daylight (SIGGRAPH 1999)</a>
 * @see <a href="https://doi.org/10.1016/0038-092X(93)90017-I">Perez, Seals, Michalsky: All-weather
 *     model for sky luminance distribution (Solar Energy, 1993)</a>
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

    /**
     * The sun's zenith angle in radians: 0 overhead, {@code PI/2} at the horizon, and clamped
     * there once the sun sets.
     *
     * <p>Past {@code PI/2} the {@code (PI - 2 * thetaS)} term in {@link #zenithLuminance()} goes
     * negative, {@code tan()} flips sign, and zenith luminance comes out negative, which the xyY
     * conversion turns into garbage colour. The clamp keeps the model inside the domain it was fit
     * to; {@link SkyModel#dayFactor()} fades it out below the horizon.
     */
    public float thetaS() {
        double cosThetaS = Math.max(-1.0, Math.min(1.0, sunDirection.y));
        return (float) Math.min(Math.acos(cosThetaS), HALF_PI);
    }

    /**
     * The Perez distribution {@code F(theta, gamma)} for one channel: a relative radiance,
     * meaningful only after dividing by its value at the zenith ({@link #zenithF()}).
     *
     * <p>{@code exp(D * gamma)} takes the raw angle while {@code E * cos^2(gamma)} takes its cosine.
     * The asymmetry is in the published function.
     *
     * @param cosTheta cosine of the angle between the view ray and straight up
     * @param gamma angle between the view ray and the sun, in radians
     * @param cosGamma cosine of {@code gamma}
     */
    private static float perez(float cosTheta, float gamma, float cosGamma, Vector5 c) {
        // max(), not "+ 0.01". Below the horizon cosTheta goes negative, which flips the sign
        // of B / cosTheta and sends exp() to +inf as the denominator nears zero.
        double cosT = Math.max(cosTheta, 0.01);
        return (float) ((1.0 + c.A() * Math.exp(c.B() / cosT))
                * (1.0 + c.C() * Math.exp(c.D() * gamma) + c.E() * cosGamma * cosGamma));
    }

    /**
     * {@code F(0, thetaS)} per channel as (Y, x, y): the Perez function at the zenith, the
     * divisor that normalises every other direction. At the zenith {@code cosTheta} is 1 and the
     * angle to the sun is {@code thetaS}. Without it the sky has the right hue at the wrong
     * brightness.
     */
    public Vector3f zenithF() {
        float thetaS = thetaS();
        float cosThetaS = (float) Math.cos(thetaS);
        return new Vector3f(
                perez(1.0f, thetaS, cosThetaS, luminanceY),
                perez(1.0f, thetaS, cosThetaS, chromaticX),
                perez(1.0f, thetaS, cosThetaS, chromaticY));
    }

    /**
     * Absolute zenith values {@code (Yz, xz, yz)}: the brightness and hue the Perez gradient is
     * scaled to.
     */
    public Vector3f zenith() {
        return new Vector3f(zenithLuminance(), zenithX(), zenithY());
    }

    /**
     * Zenith luminance {@code Yz} in kcd/m^2, as given in the paper. At turbidity 2 it is about
     * 15.5 at noon and 1.99 at sunset.
     *
     * <p>{@code tan()} stays finite for positive turbidity: {@code chi} peaks at {@code (4/9 -
     * T/120) * PI} with the sun overhead, below {@code PI/2} for any {@code T > 0}.
     */
    public float zenithLuminance() {
        double chi = (4.0 / 9.0 - turbidity / 120.0) * (Math.PI - 2.0 * thetaS());
        return (float) ((4.0453 * turbidity - 4.9710) * Math.tan(chi) - 0.2155 * turbidity + 2.4192);
    }

    /**
     * Zenith chromaticity {@code xz}: a cubic in {@code thetaS} with {@code T^2}, {@code T} and
     * constant terms, as given in the paper. {@link #zenithY()} has the same shape.
     */
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

    /**
     * The full model for one view direction, as linear sRGB before exposure. The CPU twin of the
     * per-pixel evaluation in the sky shader, for callers that need the sky colour in a direction.
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

    /**
     * CIE xyY to XYZ to linear sRGB (D65). Preetham's output is xyY; skipping this gives a sky of
     * the right shape and the wrong hue.
     *
     * @see <a href="http://www.brucelindbloom.com/index.html?Eqn_xyY_to_XYZ.html">Lindbloom: xyY to
     *     XYZ</a>
     * @see <a href="http://www.brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html">Lindbloom:
     *     RGB/XYZ matrices</a>
     */
    private static Vector3f xyYToLinearRgb(float bigY, float x, float y) {
        float safeY = Math.max(y, 1e-4f);
        float bigX = (x / safeY) * bigY;
        float bigZ = ((1.0f - x - safeY) / safeY) * bigY;
        return new Vector3f(
                3.2406f * bigX - 1.5372f * bigY - 0.4986f * bigZ,
                -0.9689f * bigX + 1.8758f * bigY + 0.0415f * bigZ,
                0.0557f * bigX - 0.2040f * bigY + 1.0570f * bigZ);
    }

    /**
     * Perez coefficient A for the three channels, packed as (Y, x, y) to match the {@code vec3}
     * uniforms the shader evaluates component-wise: one Perez call on the GPU for all three
     * channels. B to E follow the same packing.
     */
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
