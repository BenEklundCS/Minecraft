package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Mesh;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import java.util.Map;
import java.util.Optional;
import org.joml.Matrix4f;

public record DrawCall(
        Mesh mesh,
        Matrix4f transform,
        ShaderProgram shader,
        Optional<TextureAtlas> atlas,
        RenderPass pass,
        Map<String, UniformValue<?>> uniforms,
        int cascadeMask) {

    /*
     * Every cascade. The default for anything that is not a shadow call, and for shadow calls that
     * have not thought about it — drawing into a cascade that did not need this caster is wasted
     * work, never a wrong image.
     */
    public static final int ALL_CASCADES = ~0;

    // Most callers (HUD, debug, opaque chunks) draw in the opaque pass — default to it.
    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas) {
        this(mesh, transform, shader, Optional.of(atlas), RenderPass.OPAQUE, Map.of(), ALL_CASCADES);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader) {
        this(mesh, transform, shader, Optional.empty(), RenderPass.OPAQUE, Map.of(), ALL_CASCADES);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas, RenderPass pass) {
        this(mesh, transform, shader, Optional.of(atlas), pass, Map.of(), ALL_CASCADES);
    }

    // The shape this record had before cascades existed. Kept so every non-shadow call site reads
    // exactly as it did — a cascade mask means nothing to the HUD.
    public DrawCall(
            Mesh mesh,
            Matrix4f transform,
            ShaderProgram shader,
            Optional<TextureAtlas> atlas,
            RenderPass pass,
            Map<String, UniformValue<?>> uniforms) {
        this(mesh, transform, shader, atlas, pass, uniforms, ALL_CASCADES);
    }

    // Shadow calls: which cascades this caster can reach, as a bit per cascade.
    public DrawCall(
            Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas, RenderPass pass, int cascadeMask) {
        this(mesh, transform, shader, Optional.of(atlas), pass, Map.of(), cascadeMask);
    }

    public boolean castsInto(int cascade) {
        return (cascadeMask & (1 << cascade)) != 0;
    }
}
