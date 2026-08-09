#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in float aAO;
layout(location = 3) in float aFaceId;
layout(location = 4) in vec3 aTint;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec2  vUV;
out float vAO;
out float vFaceId;
out vec3  vTint;
out vec3  vViewPos;

void main() {
    vec4 viewPos = uView * uModel * vec4(aPos, 1.0);
    vViewPos = viewPos.xyz;

    gl_Position = uProjection * viewPos;
    vUV     = aUV;
    vAO     = aAO;
    vFaceId = aFaceId;
    vTint   = aTint;
}
