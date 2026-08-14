#version 330 core
in vec3 vViewDir;

uniform vec3 uHorizonColor;
uniform vec3 uZenithColor;

out vec4 FragColor;

void main() {
    vec3 dir = normalize(vViewDir);

    float t = max(dir.y, 0.0);

    FragColor = vec4(mix(uHorizonColor, uZenithColor, t), 1.0);
}
