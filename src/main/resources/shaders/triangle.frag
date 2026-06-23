#version 330 core
in vec2 texCoord;
out vec4 FragColor;
uniform sampler2D tex;
void main() {
    // OAK_LEAF tint: (0.475, 0.753, 0.353, 1.0)
    FragColor = texture(tex, texCoord) * vec4(0.475, 0.753, 0.353, 1.0);
}
