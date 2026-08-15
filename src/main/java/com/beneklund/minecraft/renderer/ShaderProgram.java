package com.beneklund.minecraft.renderer;

import static com.beneklund.minecraft.util.Log.RENDER;

import com.beneklund.minecraft.platform.graphics.GlShader;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ShaderProgram {
    private static final Path DEV_SHADER_ROOT = Path.of("src/main/resources");
    private static final Pattern INCLUDE =
            Pattern.compile("^[ \\t]*#include[ \\t]+\"([^\"]+)\"[ \\t]*$", Pattern.MULTILINE);
    private static final int MAX_INCLUDE_DEPTH = 4;

    private final String vertexShaderPath;
    private final String fragmentShaderPath;
    private GlShader shader;

    // Paths must start with '/' to be resolved from the classpath root.
    // Without the leading slash, getResourceAsStream() looks relative to this class's package.
    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        this.vertexShaderPath = vertexShaderPath;
        this.fragmentShaderPath = fragmentShaderPath;
        shader = new GlShader(loadSource(vertexShaderPath), loadSource(fragmentShaderPath));
    }

    public boolean reload() {
        GlShader next;
        try {
            next = new GlShader(loadSource(vertexShaderPath), loadSource(fragmentShaderPath));
        } catch (RuntimeException e) {
            RENDER.error("reload failed for {}, keeping the previous program", fragmentShaderPath, e);
            return false;
        }
        shader.delete();
        shader = next;
        RENDER.info("reloaded {}", fragmentShaderPath);
        return true;
    }

    public void apply(Map<String, UniformValue<?>> frame, Map<String, UniformValue<?>> call) {
        shader.apply(frame, call);
    }

    public String readSource(String path) {
        Path onDisk = DEV_SHADER_ROOT.resolve(path.startsWith("/") ? path.substring(1) : path);
        if (Files.isRegularFile(onDisk)) {
            try {
                RENDER.debug("shader source from disk: {}", onDisk);
                return Files.readString(onDisk, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader from disk: %s".formatted(onDisk), e);
            }
        }
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Failed to load shader from path: %s".formatted(path));
            }
            RENDER.debug("shader source from classpath: {}", path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader program: ", e);
        }
    }

    private String loadSource(String path) {
        return resolveIncludes(readSource(path), 0);
    }

    private String resolveIncludes(String source, int depth) {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new IllegalStateException("#include nested deeper than " + MAX_INCLUDE_DEPTH);
        }
        Matcher matcher = INCLUDE.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String included = resolveIncludes(readSource(matcher.group(1)), depth + 1);
            matcher.appendReplacement(out, Matcher.quoteReplacement(included));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public void bind() {
        shader.use();
    }

    public void setUniformInt(String name, int value) {
        shader.setInt(name, value);
    }

    public void setUniformFloat(String name, float value) {
        shader.setFloat(name, value);
    }

    public void setUniformVec2(String name, float x, float y) {
        shader.setVec2(name, x, y);
    }

    public void setUniformVec3(String name, Vector3f vec3) {
        shader.setVec3(name, vec3);
    }

    public void setUniformMat4(String name, Matrix4f matrix) {
        shader.setMatrix4(name, matrix);
    }

    public void delete() {
        shader.delete();
    }
}
