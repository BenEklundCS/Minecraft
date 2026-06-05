package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.GpuMesh;
import org.joml.Matrix4f;

public record DrawCall(GpuMesh mesh, Matrix4f transform, ShaderProgram shader) {}
