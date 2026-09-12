#version 330 core
in vec2 vScreenUV;

uniform sampler2D uOcclusion;
uniform vec2  uSunUV;
uniform float uDensity;    // fraction of the distance to the sun walked in total
uniform float uWeight;     // contribution of a single sample
uniform float uDecay;      // multiplier applied per step

out vec4 FragColor;

// Fixed, because the loop bound must be a compile-time constant in GLSL 3.30 and because the
// sample count is a quality setting rather than something to tune per frame.
const int GODRAY_SAMPLES = 64;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// The occlusion pass writes exactly vec3(0.0) for anything with geometry in front of it, so this
// only has to clear float noise and the LINEAR filtering that softens a silhouette edge. Any lit
// sky is orders of magnitude above it — scene radiance runs to 36 in the lobe around the sun.
const float OCCLUDED_EPSILON = 0.01;

// Interleaved-gradient noise. One value per pixel in [0,1), no texture and no sin() — the
// magic numbers are the standard ones and are not derivable, only cited.
float hash(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec2 delta = (vScreenUV - uSunUV) * (uDensity / float(GODRAY_SAMPLES));

    // Sixty-four evenly spaced steps put every pixel's samples on the same rings, and the rings
    // are visible as concentric arcs. Offsetting each pixel's start by a fraction of one step
    // trades that banding for noise, which the eye forgives and the blur then hides.
    vec2 pos = vScreenUV - delta * hash(gl_FragCoord.xy);

    float illumination = 1.0;
    vec3 accum = vec3(0.0);
    float blocked = 0.0;

    for (int i = 0; i < GODRAY_SAMPLES; i++) {
        pos -= delta;
        vec3 occl = texture(uOcclusion, pos).rgb;
        vec2 within = step(vec2(0.0), pos) * step(pos, vec2(1.0));
        float onScreen = within.x * within.y;

        accum += occl * illumination * uWeight * onScreen;
        blocked += (1.0 - step(OCCLUDED_EPSILON, dot(occl, LUMA))) * onScreen;
        illumination *= uDecay;
    }
    FragColor = vec4(accum * (blocked / float(GODRAY_SAMPLES)), 1.0);
}
