#version 330 core
#include "/shaders/lib/sky.glsl"
in vec3 vViewDir;
in vec2 vScreenUV;

// The clouds, already raymarched into a quarter-resolution buffer by CloudRenderer before this
// pass ran. Premultiplied: rgb is what the volume scattered toward the eye, alpha is how much of
// the sky behind it survived. Bound on its own texture unit by Renderer.submit.
uniform sampler2D uCloudBuffer;
uniform float uExposure;
out vec4 FragColor;

// The sun disc stays const rather than uniform: it survives F5 without a rebuild, and unlike
// exposure nothing on the CPU needs to agree with it. The real sun subtends about half a
// degree; slightly larger reads better before bloom exists to spread it.
const float SUN_ANGULAR_RADIUS = 0.013;
// Has to stay well clear of the sky it sits in, at every elevation. Measured at turbidity 2.5,
// the Perez lobe puts the sky within 3 degrees of the sun at 25-36 - so a disc in that range
// disappears into its own glow, and a bloom threshold cannot tell them apart. This lands the
// disc at 75 with the sun on the horizon and 211 overhead, against a sky that never passes 36.
const float SUN_INTENSITY = 270.0;

// SUN_COLOR, the per-channel extinction and the air-mass floor moved to lib/sky.glsl behind
// sunlightColor(). The clouds are lit by the same sun as this disc, and two copies of that
// model would drift - the disc going orange at sunset while the clouds it lights stayed white.

// The cloud layer moved to cloud.frag, which marches the volume into a buffer this pass reads
// back through uCloudBuffer. Nothing about clouds is computable from a bearing alone any more:
// a volume needs a march, and a march needs its own resolution.

void main() {
    vec3 dir = normalize(vViewDir);
    vec3 rgb = skyRadiance(dir);
    float gamma = acos(clamp(dot(dir, uSunDirection), -1.0, 1.0));
    float disc = smoothstep(SUN_ANGULAR_RADIUS, SUN_ANGULAR_RADIUS * 0.9, gamma);
    // Full strength until the sun's centre reaches the horizon, then out over the next ~2 degrees.
    // Centring this on zero instead made the disc half gone at the moment it touched the horizon,
    // and uDayFactor is already fading it a second time on top.
    disc *= smoothstep(-0.04, 0.0, uSunDirection.y);
    rgb += disc * sunlightColor() * SUN_INTENSITY;

    // Preetham is a daylight model - thetaS is clamped at the horizon, so below it the sky
    // would sit at sunset brightness forever. uDayFactor fades the model out over civil
    // twilight and hands off to a plain night gradient.
    //
    // No uSkyBrightness here any more. That is the terrain light floor (0.15, so caves and
    // ground stay readable at night) and it never reaches zero; multiplying by it only scaled
    // the sunset orange down instead of getting rid of it. The model's own zenith luminance
    // already falls 22.9 -> 1.88 from noon to sunset, so dusk dims itself.
    rgb = max(rgb, 0.0);

    // Clouds sit in front of everything the sky pass has computed, including the disc, so they
    // composite over rgb rather than adding into it — a cloud in front of the sun must hide it.
    // The volume arrives premultiplied, so the composite is one multiply and one add, and the same
    // transmittance that dims the sky behind a cloud dims the disc behind it.
    //
    // Sampled at quarter resolution and stretched by the sampler's own bilinear filter. Clouds are
    // low-frequency enough that the only place the upscale is visible is where one crosses the sun
    // disc, and the disc underneath it is still full resolution.
    vec4 cloud = texture(uCloudBuffer, vScreenUV);
    rgb = rgb * cloud.a + cloud.rgb;

    // Unchanged from GG-4: Preetham has no night, so the whole daylight result — clouds now
    // included — fades to the night gradient across civil twilight.
    FragColor = vec4(mix(nightRadiance(dir), rgb, uDayFactor), 1.0);
}
