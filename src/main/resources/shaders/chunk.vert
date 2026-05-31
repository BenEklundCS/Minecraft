#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in float aAO;
layout(location = 3) in float aFaceId;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec2  vUV;
out float vAO;
out float vFaceId;

void main() {
    gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
    vUV     = aUV;
    vAO     = aAO;
    vFaceId = aFaceId;
}
