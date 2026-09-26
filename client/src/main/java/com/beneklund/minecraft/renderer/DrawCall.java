package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Mesh;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import com.beneklund.minecraft.renderer.asset.ShaderProgram;
import com.beneklund.minecraft.renderer.asset.TextureAtlas;
import java.util.Map;
import java.util.Optional;
import org.joml.Matrix4f;

/**
 * One mesh to draw: what, where, with which program and texture, in which pass. Renderables return
 * these from {@link IRenderable#getDrawCalls} and {@link Renderer} owns every GL state change that
 * issuing them takes.
 *
 * <p>{@code transform} uploads as {@code uModel}, the only uniform that differs between draws in a
 * frame. {@code cascadeMask} matters only for {@link RenderPass#SHADOW} calls: bit {@code i} set
 * means the mesh can cast into shadow cascade {@code i}.
 *
 * <p>The convenience constructors default to the opaque pass, no extra uniforms, and every
 * cascade.
 */
public record DrawCall(
        Mesh mesh,
        Matrix4f transform,
        ShaderProgram shader,
        Optional<TextureAtlas> atlas,
        RenderPass pass,
        Map<String, UniformValue<?>> uniforms,
        int cascadeMask) {

    /**
     * Every cascade. The default for non-shadow calls and for shadow calls that haven't narrowed
     * it. Drawing a caster into a cascade that didn't need it costs time and never changes the
     * image.
     */
    public static final int ALL_CASCADES = ~0;

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas) {
        this(mesh, transform, shader, Optional.of(atlas), RenderPass.OPAQUE, Map.of(), ALL_CASCADES);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader) {
        this(mesh, transform, shader, Optional.empty(), RenderPass.OPAQUE, Map.of(), ALL_CASCADES);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas, RenderPass pass) {
        this(mesh, transform, shader, Optional.of(atlas), pass, Map.of(), ALL_CASCADES);
    }

    /** The shape from before cascades existed, so non-shadow call sites carry no mask. */
    public DrawCall(
            Mesh mesh,
            Matrix4f transform,
            ShaderProgram shader,
            Optional<TextureAtlas> atlas,
            RenderPass pass,
            Map<String, UniformValue<?>> uniforms) {
        this(mesh, transform, shader, atlas, pass, uniforms, ALL_CASCADES);
    }

    /** For shadow casters: {@code cascadeMask} holds one bit per cascade the caster can reach. */
    public DrawCall(
            Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas, RenderPass pass, int cascadeMask) {
        this(mesh, transform, shader, Optional.of(atlas), pass, Map.of(), cascadeMask);
    }

    public boolean castsInto(int cascade) {
        return (cascadeMask & (1 << cascade)) != 0;
    }
}
