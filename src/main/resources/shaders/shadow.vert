#version 330 core
layout(location = 0) in vec3 aPos;

uniform mat4 uModel;
// One cascade's matrix, set by the renderer before each cascade's draws. Not the array
// chunk.frag reads: a vertex shader has no fragment depth to select a cascade with, and does not
// need one — the pass it is running in already decided.
uniform mat4 uCascadeViewProj;

void main() {
    gl_Position = uCascadeViewProj * uModel * vec4(aPos, 1.0);
}
