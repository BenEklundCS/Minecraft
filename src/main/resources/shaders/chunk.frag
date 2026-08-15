#version 330 core
#include "/shaders/lib/sky.glsl"
in vec2  vUV;
in float vAO;
in float vFaceId;
in vec3  vTint;
in vec3  vViewPos;
in vec2  vLight;

uniform sampler2D uAtlas;
uniform float     uSkyBrightness;
uniform float     uExtinction;
uniform mat4      uInvViewRotation;
uniform float     uCameraY;

// The altitude over which air density falls by 1/e. Earth's is about 8.5 km against a ~9 km
// visual range; this world's sight line is ~256 blocks, so 120 keeps the same rough proportion.
const float ATMOSPHERE_SCALE_HEIGHT = 120.0;
// Density is 1.0 here rather than at y=0, so uExtinction keeps meaning what it meant before this
// term existed: the per-block coefficient at ground level. Referencing it to y=0 instead would
// silently rescale every tuned value by exp(-62/120) = 0.6.
const float HAZE_REFERENCE_Y = 62.0;

out vec4 FragColor;

float densityAt(float y) { return exp(-(y - HAZE_REFERENCE_Y) / ATMOSPHERE_SCALE_HEIGHT); }

void main() {
    vec4 texColor = texture(uAtlas, vUV);
    if (texColor.a < 0.1) discard;

    // Three brightness bands driven by faceId:
    //   0 (UP) = 1.0,  1 (sides) = 0.8,  2 (DOWN) = 0.6
    float faceBrightness = (vFaceId < 0.5) ? 1.0
                         : (vFaceId < 2.5) ? 0.8 : 0.6;

    vec3 worldDir = normalize(mat3(uInvViewRotation) * vViewPos);

    // sky * brightness of sky (derived from time of day) | block
    float light = max(vLight.x * uSkyBrightness, vLight.y);
    vec3 lit = texColor.rgb * vAO * light * faceBrightness * vTint;
    float dist = length(vViewPos);
    // Into the same radiance units the sky writes, or the tonemap in post.frag crushes a fully
    // lit face to 11%. Solved against ACES for 0.90 out at light=1: aces(scale * uExposure) = 0.9
    // reduces to 0.323x^2 - 0.501x - 0.126 = 0, x = 1.771, scale = x / 0.115.
    const float RADIANCE_SCALE = 15.4;

    // Averaging the density at the two ends of the ray rather than integrating along it. The
    // exact integral through an exponential atmosphere has a closed form, but over a few hundred
    // blocks the average is within a few percent of it and costs two instructions.
    //
    // worldDir is a unit vector and the camera sits at the origin in view space, so the
    // fragment's height is the camera's plus however far the ray climbed. That is the whole
    // reason a world-space position varying was not needed.
    float fragmentY = uCameraY + worldDir.y * dist;
    float density = 0.5 * (densityAt(uCameraY) + densityAt(fragmentY));

    float transmittance = exp(-dist * uExtinction * density);
    vec3 inscatter = mix(nightRadiance(worldDir), skyRadiance(worldDir), uDayFactor);
    FragColor = vec4(mix(inscatter, lit * RADIANCE_SCALE, transmittance), texColor.a);
}
