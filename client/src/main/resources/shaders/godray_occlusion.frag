#version 330 core
in vec2 vScreenUV;

uniform sampler2D uScene;
uniform sampler2D uSceneDepth;

out vec4 FragColor;

/*
 * Everything the blur is allowed to smear. Sky keeps its radiance; anything with geometry in front
 * of it is black, so a pixel whose path toward the sun crosses a silhouette accumulates nothing
 * along that stretch. That gap is the shaft.
 *
 * Sky is identified by depth, not by colour: a bright cloud and a bright snow slope are the same
 * colour and only one of them should be smeared.
 */
void main() {
    float depth = texture(uSceneDepth, vScreenUV).r;

    // The clear value. Anything nearer is an occluder.
    bool isSky = depth >= 1.0;

    FragColor = vec4(isSky ? texture(uScene, vScreenUV).rgb : vec3(0.0), 1.0);
}
