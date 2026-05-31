#version 330 core
in vec2  vUV;
in float vAO;
in float vFaceId;

uniform sampler2D uAtlas;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(uAtlas, vUV);
    if (texColor.a < 0.1) discard;

    // Three brightness bands driven by faceId:
    //   0 (UP) = 1.0,  1 (sides) = 0.8,  2 (DOWN) = 0.6
    float faceBrightness = (vFaceId < 0.5) ? 1.0
                         : (vFaceId < 2.5) ? 0.8 : 0.6;

    FragColor = vec4(texColor.rgb * vAO * faceBrightness, texColor.a);
}
