#version 330 core
in vec2 vScreenUV;

uniform sampler2DArray uDepth;
// Which cascade to show. F6 cycles it; the near cascade is layer 0.
uniform float uLayer;
// The slice of the [0,1] depth range to spread across black-to-white. The shadow map's
// projection is orthographic, so its depth is linear — but terrain only occupies a few hundred
// blocks of a ~1200 block slab, so the interesting values sit in a narrow band around the middle
// and a raw view of them is a flat grey. These stretch that band to fill the contrast range.
uniform float uDepthMin;
uniform float uDepthMax;

out vec4 FragColor;

void main() {
    float d = texture(uDepth, vec3(vScreenUV, uLayer)).r;

    // Exactly 1.0 means nothing was ever rendered there — the clear value. Flagged red rather
    // than drawn as white, because "the map is empty" and "the map is full of distant geometry"
    // are the two cases this view exists to tell apart, and they are both pale otherwise.
    if (d >= 0.999) {
        FragColor = vec4(0.45, 0.05, 0.05, 1.0);
        return;
    }

    float v = clamp((d - uDepthMin) / (uDepthMax - uDepthMin), 0.0, 1.0);

    // Near the light is bright. The eye reads "closer to the sun = lit" faster than the raw
    // convention, where near is 0 and would draw the casters black.
    FragColor = vec4(vec3(1.0 - v), 1.0);
}
