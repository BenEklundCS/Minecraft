#version 330 core
layout(location = 0) in vec2 aPos;

uniform mat4 uInvViewProj;

out vec3 vViewDir;

void main() {
    vec4 far = uInvViewProj * vec4(aPos, 1.0, 1.0);
    vViewDir = far.xyz / far.w;
    gl_Position = vec4(aPos, 1.0, 1.0);
}
