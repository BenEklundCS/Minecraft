package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.GlShader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;

public class ShaderProgram {
    private final GlShader shader;

    // Paths must start with '/' to be resolved from the classpath root.
    // Without the leading slash, getResourceAsStream() looks relative to this class's package.
    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        try {
            String vertexShaderSource;
            try (InputStream vertexShaderStream = getClass().getResourceAsStream(vertexShaderPath)) {
                if (vertexShaderStream == null) {
                    throw new IOException("Failed to load vertex shader from path: %s".formatted(vertexShaderPath));
                }
                vertexShaderSource = new String(vertexShaderStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            String fragmentShaderSource;
            try (InputStream fragmentShaderStream = getClass().getResourceAsStream(fragmentShaderPath)) {
                if (fragmentShaderStream == null) {
                    throw new IOException("Failed to load fragment shader from path: %s".formatted(fragmentShaderPath));
                }
                fragmentShaderSource = new String(fragmentShaderStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            shader = new GlShader(vertexShaderSource, fragmentShaderSource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader program: ", e);
        }
    }

    public void bind() {
        shader.use();
    }

    public void setUniformMat4(String name, Matrix4f matrix) {
        shader.setMatrix4(name, matrix);
    }

    public void delete() {
        shader.delete();
    }
}
