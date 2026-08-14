#version 330 core
layout(location = 0) in vec2 aPos;

out vec2 vScreenUV;

/*
 * The same fullscreen triangle SkyMesh feeds sky.vert, read differently. sky.vert unprojects
 * each corner into a view ray; here the position is already in clip space, so it goes straight
 * to gl_Position and the only work is mapping [-1, 1] onto the [0, 1] the sampler wants.
 *
 * The triangle runs to +3, past the screen on two sides, which is why UVs run past 1.0 as well -
 * the rasteriser clips the excess and what survives covers the viewport exactly. One triangle
 * instead of a two-triangle quad means no seam down the diagonal for the sampler to shimmer on.
 */
void main() {
    vScreenUV = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
