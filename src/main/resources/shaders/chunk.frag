#version 330 core
in vec2  vUV;
in float vAO;
in float vFaceId;
in vec3  vTint;
in vec3  vViewPos;
in vec2  vLight;

uniform sampler2D uAtlas;
uniform vec3      uFogColor;
uniform float     uFogStart;
uniform float     uFogEnd;
uniform float     uSkyBrightness;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(uAtlas, vUV);
    if (texColor.a < 0.1) discard;

    // Three brightness bands driven by faceId:
    //   0 (UP) = 1.0,  1 (sides) = 0.8,  2 (DOWN) = 0.6
    float faceBrightness = (vFaceId < 0.5) ? 1.0
                         : (vFaceId < 2.5) ? 0.8 : 0.6;

    // sky * brightness of sky (derived from time of day) | block
    float light = max(vLight.x * uSkyBrightness, vLight.y);
    vec3 lit = texColor.rgb * vAO * light * faceBrightness * vTint;
    float dist = length(vViewPos);
    float fogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    // Into the same radiance units the sky writes, or the tonemap in post.frag crushes a fully
    // lit face to 11%. Solved against ACES for 0.90 out at light=1: aces(scale * uExposure) = 0.9
    // reduces to 0.323x^2 - 0.501x - 0.126 = 0, x = 1.771, scale = x / 0.115.
    const float RADIANCE_SCALE = 15.4;
    FragColor = vec4(mix(lit, uFogColor, fogFactor) * RADIANCE_SCALE, texColor.a);
}
