package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.Mesh;
import org.joml.Matrix4f;

public record DrawCall(Mesh mesh, Matrix4f transform, ShaderProgram shader) {}
