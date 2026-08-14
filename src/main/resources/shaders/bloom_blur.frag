#version 330 core
in vec2 vScreenUV;

uniform sampler2D uSource;
uniform vec2 uTexelSize;   // 1.0 / textureSize, so one step lands exactly one texel over
uniform vec2 uDirection;   // (1, 0) horizontal, (0, 1) vertical

out vec4 FragColor;

/*
 * One shader, run twice. A 2D Gaussian is separable - rows then columns gives the same result as
 * a full 2D kernel - so 9 taps each way is 18 samples instead of 81. uDirection is what makes one
 * file serve both passes.
 *
 * The weights sum to 1.0 (0.227027 + 2 * (0.194594 + 0.121621 + 0.054054 + 0.016216)). Anything
 * else and each pass brightens or darkens the image, which compounds across the two.
 */
const float WEIGHTS[5] = float[](0.227027, 0.194594, 0.121621, 0.054054, 0.016216);

void main() {
    vec2 step = uTexelSize * uDirection;
    vec3 sum = texture(uSource, vScreenUV).rgb * WEIGHTS[0];

    for (int i = 1; i < 5; i++) {
        sum += texture(uSource, vScreenUV + step * float(i)).rgb * WEIGHTS[i];
        sum += texture(uSource, vScreenUV - step * float(i)).rgb * WEIGHTS[i];
    }

    FragColor = vec4(sum, 1.0);
}
