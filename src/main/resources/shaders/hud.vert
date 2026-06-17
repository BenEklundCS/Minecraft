#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aColor;

out vec3 vColor;

// HUD geometry is already in clip space (-1..1), so no camera transform.
// The vertices pass straight through and cover the screen.
void main() {
    gl_Position = vec4(aPos, 1.0);
    vColor = aColor;
}
