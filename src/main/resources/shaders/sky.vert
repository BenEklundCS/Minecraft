#version 330 core
layout(location = 0) in vec2 aPos;

uniform mat4 uInvViewProj;

out vec3 vViewDir;

// Where this fragment lands on screen, for sampling passes that already ran into a buffer of their
// own - the clouds. Computed here rather than from gl_FragCoord and a viewport size uniform
// because aPos is already in clip space and the rasteriser is already interpolating it. Unused by
// cloud.frag, which shares this vertex shader; an unread vertex output costs nothing.
out vec2 vScreenUV;

void main() {
    vec4 far = uInvViewProj * vec4(aPos, 1.0, 1.0);
    vViewDir = far.xyz / far.w;
    vScreenUV = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 1.0, 1.0);
}
