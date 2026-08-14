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

// The night handoff. Preetham has no night, so SkyModel fades it out across civil twilight and
// supplies what to fade into. These arrive as uniforms rather than living here as consts
// because the fog colour is computed from the same blend on the CPU, and fog meeting a sky it
// disagrees with is exactly the horizon seam this shader exists to avoid.
uniform float uDayFactor;
uniform vec3 uNightHorizon;
uniform vec3 uNightZenith;

out vec4 FragColor;

// The sun disc stays const rather than uniform: it survives F5 without a rebuild, and unlike
// exposure nothing on the CPU needs to agree with it. The real sun subtends about half a
// degree; slightly larger reads better before bloom exists to spread it.
const float SUN_ANGULAR_RADIUS = 0.013;
const vec3 SUN_COLOR = vec3(1.0, 0.95, 0.86);
// Has to stay well clear of the sky it sits in, at every elevation. Measured at turbidity 2.5,
// the Perez lobe puts the sky within 3 degrees of the sun at 25-36 - so a disc in that range
// disappears into its own glow, and a bloom threshold cannot tell them apart. This lands the
// disc at 75 with the sun on the horizon and 211 overhead, against a sky that never passes 36.
const float SUN_INTENSITY = 270.0;

// Per-channel extinction, blue scattered out hardest - the same reason the sky is blue and the
// setting sun is red. Preetham dims the *sky* toward the horizon on its own; the disc term is a
// flat add and gets none of that, so without this the sun holds full noon radiance at sunset and
// ends up ~23x the sky instead of ~3x. Bloom is what makes that visible.
const vec3 SUN_EXTINCTION = vec3(0.09, 0.22, 0.45);
// Air mass is 1/sin(elevation), which runs away at the horizon. Capping the elevation at 0.15
// caps air mass near 6.7 - enough to redden and dim the disc without deleting it.
const float MIN_SUN_ELEVATION = 0.15;

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
    // Full strength until the sun's centre reaches the horizon, then out over the next ~2 degrees.
    // Centring this on zero instead made the disc half gone at the moment it touched the horizon,
    // and uDayFactor is already fading it a second time on top.
    disc *= smoothstep(-0.04, 0.0, uSunDirection.y);
    float airMass = 1.0 / max(uSunDirection.y, MIN_SUN_ELEVATION);
    vec3 sunTransmittance = exp(-SUN_EXTINCTION * airMass);
    rgb += disc * SUN_COLOR * SUN_INTENSITY * sunTransmittance;

    // Preetham is a daylight model - thetaS is clamped at the horizon, so below it the sky
    // would sit at sunset brightness forever. uDayFactor fades the model out over civil
    // twilight and hands off to a plain night gradient.
    //
    // No uSkyBrightness here any more. That is the terrain light floor (0.15, so caves and
    // ground stay readable at night) and it never reaches zero; multiplying by it only scaled
    // the sunset orange down instead of getting rid of it. The model's own zenith luminance
    // already falls 22.9 -> 1.88 from noon to sunset, so dusk dims itself.
    rgb = max(rgb, 0.0);
    vec3 night = mix(uNightHorizon, uNightZenith, clamp(dir.y, 0.0, 1.0));
    FragColor = vec4(mix(night, rgb, uDayFactor), 1.0);
}
