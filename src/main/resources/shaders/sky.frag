#version 330 core
#include "/shaders/lib/sky.glsl"
in vec3 vViewDir;

uniform float uExposure;
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

void main() {
    vec3 dir = normalize(vViewDir);
    vec3 rgb = skyRadiance(dir);
    float gamma = acos(clamp(dot(dir, uSunDirection), -1.0, 1.0));
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
    FragColor = vec4(mix(nightRadiance(dir), rgb, uDayFactor), 1.0);
}
