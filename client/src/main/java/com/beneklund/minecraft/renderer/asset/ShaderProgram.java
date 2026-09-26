package com.beneklund.minecraft.renderer.asset;

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

/**
 * A {@link GlShader} built from shader files by path, with {@code #include} expansion and hot
 * reload.
 *
 * <p>Sources are read from disk under {@code src/main/resources}, resolved against the working
 * directory, when the file exists there, and from the classpath otherwise. When the working
 * directory holds the shader sources, a reload picks up edits without rebuilding resources.
 *
 * <p>A line of the form {@code #include "/shaders/lib/x.glsl"} is replaced by that file's
 * contents, recursively to a depth of {@value #MAX_INCLUDE_DEPTH}. GLSL has no include directive of
 * its own, so this is textual substitution before compilation; an included file must not contain
 * its own {@code #version} line.
 */
public class ShaderProgram {
    private static final Path DEV_SHADER_ROOT = Path.of("src/main/resources");
    private static final Pattern INCLUDE =
            Pattern.compile("^[ \\t]*#include[ \\t]+\"([^\"]+)\"[ \\t]*$", Pattern.MULTILINE);
    private static final int MAX_INCLUDE_DEPTH = 4;

    private final String vertexShaderPath;
    private final String fragmentShaderPath;
    private GlShader shader;

    /**
     * Compiles and links the program. Paths start with {@code /}; without it, the classpath lookup
     * resolves relative to this class's package.
     *
     * @throws RuntimeException if a source is missing or the program fails to compile or link
     */
    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        this.vertexShaderPath = vertexShaderPath;
        this.fragmentShaderPath = fragmentShaderPath;
        shader = new GlShader(loadSource(vertexShaderPath), loadSource(fragmentShaderPath));
    }

    /**
     * Rebuilds the program from source. On a compile or link failure the error is logged and the
     * previous program stays in use, so a typo during live editing leaves the game running.
     *
     * <p>Uniform values live on the program object, so a reloaded program starts with every
     * uniform at its default. Callers that set a uniform once must set it again.
     *
     * @return whether the new program replaced the old one
     */
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

    /**
     * Uploads frame uniforms; see {@link GlShader#apply}. Call {@link #bind()} first, because
     * {@code glUniform*} writes into the active program.
     */
    public void apply(long frame, Map<String, UniformValue<?>> uniforms) {
        shader.apply(frame, uniforms);
    }

    /** One source file, unexpanded, from disk if present under the dev root, else the classpath. */
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
