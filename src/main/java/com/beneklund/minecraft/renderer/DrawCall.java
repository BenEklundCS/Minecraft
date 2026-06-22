package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Mesh;
import org.joml.Matrix4f;

public record DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader, RenderPass pass) {
    // Most callers (HUD, debug, opaque chunks) draw in the opaque pass — default to it.
    public DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader) {
        this(mesh, transform, shader, RenderPass.OPAQUE);
    }
}
