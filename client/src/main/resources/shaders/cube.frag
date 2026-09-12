#version 330 core
in vec2 texCoord;
in vec3 vertTint;
out vec4 FragColor;
uniform sampler2D tex;
void main() {
    // Grayscale textures (grass_top, etc.) carry no color; the per-vertex tint
    // multiplies it in. White (1,1,1) leaves a face untouched.
    FragColor = texture(tex, texCoord) * vec4(vertTint, 1.0);
}
