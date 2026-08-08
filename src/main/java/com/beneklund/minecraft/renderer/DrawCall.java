package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Mesh;
import java.util.Optional;
import org.joml.Matrix4f;

public record DrawCall(
        Mesh mesh, Matrix4f transform, ShaderProgram shader, Optional<TextureAtlas> atlas, RenderPass pass) {
    // Most callers (HUD, debug, opaque chunks) draw in the opaque pass — default to it.
    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas) {
        this(mesh, transform, shader, Optional.of(atlas), RenderPass.OPAQUE);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader) {
        this(mesh, transform, shader, Optional.empty(), RenderPass.OPAQUE);
    }

    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, TextureAtlas atlas, RenderPass pass) {
        this(mesh, transform, shader, Optional.of(atlas), pass);
    }
}
