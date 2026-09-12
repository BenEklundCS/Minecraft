#version 330 core
in vec2 vScreenUV;

uniform sampler2D uScene;
uniform float uExposure;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform sampler2D uGodray;
uniform float uGodrayStrength;

out vec4 FragColor;

vec3 tonemapACES(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 hdr = (texture(uScene, vScreenUV).rgb
    + texture(uBloom, vScreenUV).rgb * uBloomStrength
    + texture(uGodray, vScreenUV).rgb * uGodrayStrength) * uExposure;
    vec3 mapped = tonemapACES(hdr);
    FragColor = vec4(mapped, 1.0);
}