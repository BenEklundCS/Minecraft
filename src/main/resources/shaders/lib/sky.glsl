// Preetham coefficients, each packed (luminance Y, chromatic x, chromatic y) so one perez()
// call evaluates all three channels at once. Built per frame by PreethamSky on the CPU -
// they depend only on turbidity and the sun, never on the view ray.
uniform vec3 uA;
uniform vec3 uB;
uniform vec3 uC;
uniform vec3 uD;
uniform vec3 uE;
uniform vec3 uZenith;   // (Yz, xz, yz) - absolute values, where brightness and hue come from
uniform vec3 uZenithF;  // F(0, thetaS) per channel - the normalisation divisor

uniform vec3 uSunDirection;

// The night handoff. Preetham has no night, so SkyModel fades it out across civil twilight and
// supplies what to fade into. These arrive as uniforms rather than living here as consts
// because the fog colour is computed from the same blend on the CPU, and fog meeting a sky it
// disagrees with is exactly the horizon seam this shader exists to avoid.
uniform float uDayFactor;
uniform vec3 uNightHorizon;
uniform vec3 uNightZenith;

/*
 * Perez et al. 1993, the distribution Preetham fits daylight to. Returns a *relative*
 * radiance - meaningless until divided by its own value at the zenith (uZenithF).
 *
 * The asymmetry is real and not a transcription slip: exp(D * gamma) takes the raw angle
 * while E * cosGamma^2 takes the cosine. That is how the function is defined.
 */
vec3 perez(float cosTheta, float gamma, float cosGamma) {
    // max(), not "+ 0.01". Below the horizon cosTheta goes negative, which flips the sign of
    // B / cosTheta and sends exp() to +inf as the denominator nears zero - a ring of blown
    // white just under the horizon line.
    float cosT = max(cosTheta, 0.01);
    return (1.0 + uA * exp(uB / cosT)) * (1.0 + uC * exp(uD * gamma) + uE * cosGamma * cosGamma);
}

vec3 skyRadiance(vec3 dir) {
    float cosTheta = dir.y;
    // clamp before acos: a normalised dot can land at 1.0000001 from float error, and
    // acos() of that is NaN, which propagates to a black or white pixel.
    float cosGamma = clamp(dot(dir, uSunDirection), -1.0, 1.0);
    float gamma = acos(cosGamma);

    vec3 xyY = uZenith * perez(cosTheta, gamma, cosGamma) / uZenithF;

    float Y = xyY.x;
    float x = xyY.y;
    // Chromatic y is the denominator of the xyY -> XYZ conversion, so it cannot reach zero.
    float y = max(xyY.z, 1e-4);

    vec3 XYZ = vec3((x / y) * Y, Y, ((1.0 - x - y) / y) * Y);

    return vec3(
    dot(XYZ, vec3(3.2406, -1.5372, -0.4986)),
    dot(XYZ, vec3(-0.9689, 1.8758, 0.0415)),
    dot(XYZ, vec3(0.0557, -0.2040, 1.0570)));
}

vec3 nightRadiance(vec3 dir) {
    return mix(uNightHorizon, uNightZenith, clamp(dir.y, 0.0, 1.0));
}

