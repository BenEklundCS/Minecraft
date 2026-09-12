#version 330 core
in vec2 vScreenUV;

uniform sampler2D uScene;
uniform float uBloomThreshold;

out vec4 FragColor;

/*
 * Keeps only what is bright enough to count as a light source, so the blur that follows has
 * something to spread. The threshold is in scene radiance units - the same scale chunk.frag and
 * sky.frag write in, before exposure - because "is this a light source" is a fact about the
 * scene and shouldn't change when the frame is exposed differently.
 */
void main() {
    vec3 c = texture(uScene, vScreenUV).rgb;
    // Rec.709 weights. Green dominates because that's where the eye is most sensitive.
    float brightness = dot(c, vec3(0.2126, 0.7152, 0.0722));
    FragColor = vec4(brightness > uBloomThreshold ? c : vec3(0.0), 1.0);
}
