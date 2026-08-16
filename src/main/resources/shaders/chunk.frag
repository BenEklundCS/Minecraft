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
uniform mat4      uLightViewProj;
uniform sampler2D uShadowMap;
uniform vec3      uCameraPos;

// The altitude over which air density falls by 1/e. Earth's is about 8.5 km against a ~9 km
// visual range; this world's sight line is ~256 blocks, so 120 keeps the same rough proportion.
const float ATMOSPHERE_SCALE_HEIGHT = 120.0;
// Density is 1.0 here rather than at y=0, so uExtinction keeps meaning what it meant before this
// term existed: the per-block coefficient at ground level. Referencing it to y=0 instead would
// silently rescale every tuned value by exp(-62/120) = 0.6.
const float HAZE_REFERENCE_Y = 62.0;

out vec4 FragColor;

float densityAt(float y) { return exp(-(y - HAZE_REFERENCE_Y) / ATMOSPHERE_SCALE_HEIGHT); }

// How much sky light a shadowed fragment keeps. A surface the sun cannot see is not black —
// it is still lit by the whole dome of the sky, which is what makes shadows read as blue-ish
// rather than as holes.
const float SHADOW_AMBIENT = 0.35;
// Depth margin, supplied already converted into the map's [0,1] units. Authored in blocks on
// the Java side because a normalized figure means nothing without knowing the light's depth
// range — at SHADOW_FAR-SHADOW_NEAR = 1199, a "standard" 0.0015 is 1.8 blocks, which is larger
// than most casters and makes their shadows vanish entirely.
uniform float uShadowBias;
// Multiplier on the per-fragment depth slope, in the map's [0,1] depth units. Scales the margin
// up on surfaces oblique to the sun, which is where a fixed bias fails first.
const float SHADOW_SLOPE_SCALE = 2.0;

float shadowFactor(vec3 worldPos, vec3 normal) {
    vec4 lightClip = uLightViewProj * vec4(worldPos, 1.0);

    // Clip -> NDC. For an orthographic projection w is always 1, so this divide changes
    // nothing today. It is here because it is what makes the function correct if the light
    // ever becomes perspective, and because leaving it out teaches the wrong shape.
    vec3 ndc = lightClip.xyz / lightClip.w;

    // NDC runs -1..1 on all three axes in GL. Texture coordinates run 0..1, and so do the
    // depth values stored in the map. Both xy and z need the same remap, which is why this
    // is one line and not two.
    vec3 lightUV = ndc * 0.5 + 0.5;

    // Past the light's far plane nothing was ever rendered, so the map holds its clear value
    // and a comparison would report "shadowed" for the whole world beyond the box.
    if (lightUV.z > 1.0) return 1.0;

    // Slope-scaled bias, from world-space geometry rather than screen-space derivatives.
    //
    // The depth error across one shadow texel grows with how steeply the surface runs away from
    // the light, so the margin has to grow with it. The tempting measure is fwidth(lightUV.z) —
    // but that is a *screen*-space derivative, so it changes when the camera rotates even though
    // the surface has not moved. That makes the bias view-dependent and every fragment near the
    // threshold flips as you turn: shadows that flicker when you move the mouse.
    //
    // tan(angle between surface and light) depends only on the normal and the sun, both world
    // space, so it holds still while you look around.
    float ndotl = clamp(abs(dot(normal, uSunDirection)), 0.05, 1.0);
    float slope = sqrt(1.0 - ndotl * ndotl) / ndotl;
    float bias = uShadowBias * (1.0 + SHADOW_SLOPE_SCALE * slope);

    // Percentage-closer filtering: average the COMPARISONS over a 3x3 neighbourhood, never the
    // depths. The mean of "nearer" and "further" is not a depth that means anything; the mean of
    // nine booleans is a coverage fraction, which is what a soft edge is. This also stops a
    // fragment sitting on the threshold from flipping wholesale between frames — it moves by a
    // ninth at a time instead.
    vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
    float sum = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float nearest = texture(uShadowMap, lightUV.xy + vec2(x, y) * texel).r;
            sum += lightUV.z - bias > nearest ? 0.0 : 1.0;
        }
    }
    return sum / 9.0;
}

void main() {
    vec4 texColor = texture(uAtlas, vUV);
    if (texColor.a < 0.1) discard;

    // faceId is Direction.ordinal(): 0=UP, 1=DOWN, 2=N, 3=S, 4=E, 5=W. Three brightness bands
    // still, they just no longer line up with a single comparison.
    int faceId = int(vFaceId + 0.5);
    float faceBrightness = (faceId == 0) ? 1.0
                         : (faceId == 1) ? 0.6 : 0.8;

    // The fragment's offset from the eye, rotated into world space. Kept as a vector rather
    // than rebuilt from a direction and a distance: normalize(R*v) * length(v) is algebraically
    // R*v, but in float32 the divide-then-multiply loses centimetres at a few hundred blocks,
    // and the error shifts as the camera rotates. Against a 0.125-block shadow texel that is
    // enough to change which texel the lookup lands in, which is shadows flickering when you
    // look around while standing still.
    vec3 worldOffset = mat3(uInvViewRotation) * vViewPos;
    vec3 worldDir = normalize(worldOffset);

    float dist = length(vViewPos);
    vec3 worldPos = uCameraPos + worldOffset;

    /*
     * The surface normal, straight off the vertex attribute the mesher wrote.
     *
     * This was previously recovered with normalize(cross(dFdx(worldPos), dFdy(worldPos))). Those
     * are screen-space derivatives, which makes the normal a function of the camera: at grazing
     * viewing angles the two derivatives run nearly parallel, their cross product collapses, and
     * the result lands on the wrong axis. Since the normal feeds the slope-scaled bias below, and
     * that bias spans a factor of 40 between a face-on and an edge-on surface, whole faces swapped
     * between lit and shadowed as the mouse moved — 4 to 7% of terrain pixels changed normal from
     * a 0.04 degree turn.
     *
     * Nothing about a cube face's normal depends on the viewer, so nothing here may. Keep it that
     * way: no dFdx, no dFdy, no fwidth anywhere in this file. ShaderSourceTest enforces it.
     */
    const vec3 FACE_NORMALS[6] = vec3[6](
        vec3( 0.0,  1.0,  0.0),   // 0 UP
        vec3( 0.0, -1.0,  0.0),   // 1 DOWN
        vec3( 0.0,  0.0, -1.0),   // 2 NORTH
        vec3( 0.0,  0.0,  1.0),   // 3 SOUTH
        vec3( 1.0,  0.0,  0.0),   // 4 EAST
        vec3(-1.0,  0.0,  0.0)    // 5 WEST
    );
    vec3 normal = FACE_NORMALS[clamp(faceId, 0, 5)];

    /*
     * Lambert. How much sun a surface receives depends on the angle it presents to it, and until
     * now nothing here said so — faceBrightness is a fixed band per face, so a wall the sun was
     * behind still took the full sun term and relied entirely on the shadow map to darken it.
     *
     * That was the worst possible place to lean on the map. A surface nearly edge-on to the light
     * is exactly where one shadow texel spans the most depth, so the comparison there is closest
     * to meaningless — and it was being asked to carry the whole result on its own.
     *
     * Writable now and not before: N.L needs a normal, and the normal used to come from
     * screen-space derivatives. Weighting the sun by a camera-dependent quantity would have put
     * the flicker into the lighting itself — measured, that was worse than leaving it out.
     */
    float ndotl = max(dot(normal, uSunDirection), 0.0);

    /*
     * A face the sun is behind is occluded by its own block, so there is nothing to look up.
     * Skipping it is free and strictly better: those are the most oblique surfaces, where the
     * texel-centre depth error is largest, so they produced a large share of the acne.
     *
     * The branch is legal because shadowFactor no longer uses screen-space derivatives — those
     * are undefined inside non-uniform control flow, which is why this could not be written while
     * the normal came from dFdx.
     */
    float shadow = ndotl > 0.0 ? shadowFactor(worldPos, normal) : 0.0;

    // Sun actually reaching this fragment: the angle it presents, times whether anything is in
    // the way. The shadow attenuates the *sun* term only — vLight.y is torches, which the sun
    // cannot block, and vLight.x is baked sky visibility, a world property rather than a view one.
    float sunVisibility = ndotl * shadow;
    float sunLit = vLight.x * uSkyBrightness * mix(SHADOW_AMBIENT, 1.0, sunVisibility);
    float light = max(sunLit, vLight.y);
    vec3 lit = texColor.rgb * vAO * light * faceBrightness * vTint;
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
    float fragmentY = uCameraPos.y + worldDir.y * dist;
    float density = 0.5 * (densityAt(uCameraPos.y) + densityAt(fragmentY));

    float transmittance = exp(-dist * uExtinction * density);
    vec3 inscatter = mix(nightRadiance(worldDir), skyRadiance(worldDir), uDayFactor);
    FragColor = vec4(mix(inscatter, lit * RADIANCE_SCALE, transmittance), texColor.a);
}
