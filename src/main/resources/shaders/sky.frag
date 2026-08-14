#version 330 core
in vec3 vViewDir;

uniform vec3 uSunDirection;

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

uniform float uExposure;
uniform float uSkyBrightness;

out vec4 FragColor;

// The sun disc stays const rather than uniform: it survives F5 without a rebuild, and unlike
// exposure nothing on the CPU needs to agree with it. The real sun subtends about half a
// degree; slightly larger reads better before bloom exists to spread it.
const float SUN_ANGULAR_RADIUS = 0.013;
const vec3 SUN_COLOR = vec3(1.0, 0.95, 0.86);
const float SUN_INTENSITY = 42.0;

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

void main() {
    // Interpolating three unit vectors across the triangle does not preserve length.
    vec3 dir = normalize(vViewDir);

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

    vec3 rgb = vec3(
        dot(XYZ, vec3(3.2406, -1.5372, -0.4986)),
        dot(XYZ, vec3(-0.9689, 1.8758, 0.0415)),
        dot(XYZ, vec3(0.0557, -0.2040, 1.0570)));

    float disc = smoothstep(SUN_ANGULAR_RADIUS, SUN_ANGULAR_RADIUS * 0.9, gamma);
    disc *= smoothstep(-0.03, 0.03, uSunDirection.y);
    rgb += disc * SUN_COLOR * SUN_INTENSITY;

    rgb = max(rgb, 0.0);
    vec3 color = vec3(1.0) - exp(-uExposure * rgb);

    FragColor = vec4(color * uSkyBrightness, 1.0);
}
