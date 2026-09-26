package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_MAT4;
import static org.lwjgl.opengl.GL20C.GL_FLOAT_VEC3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A typed value for one frame-wide uniform, uploaded by {@link GlShader#apply}.
 *
 * <p>The sealed set matches the GLSL types {@link GlShader#apply} drives: {@code float}, {@code
 * vec3} and {@code mat4}. Adding a type means a record here, a case in {@link #matches}, a case in
 * {@code GlShader.upload}, and the GL type in {@code GlShader.drivenByFrameUniforms}. The two
 * switches are exhaustive and fail to compile until they have the case; the type check doesn't,
 * and a type missing there is never uploaded.
 */
public sealed interface UniformValue<T> {
    record F(float v) implements UniformValue<Float> {
        public Float value() {
            return v;
        }
    }
    ;

    record V3(Vector3f v) implements UniformValue<Vector3f> {
        public Vector3f value() {
            return v;
        }
    }
    ;

    record M4(Matrix4f v) implements UniformValue<Matrix4f> {
        public Matrix4f value() {
            return v;
        }
    }
    ;

    /** Whether {@code value} can be uploaded to a uniform GL reports as {@code glType}. */
    static boolean matches(int glType, UniformValue<?> value) {
        return switch (value) {
            case UniformValue.F ignored -> glType == GL_FLOAT;
            case UniformValue.V3 ignored -> glType == GL_FLOAT_VEC3;
            case UniformValue.M4 ignored -> glType == GL_FLOAT_MAT4;
        };
    }

    T value();
}
