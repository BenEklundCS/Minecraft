package com.beneklund.minecraft.platform.graphics;

import static com.beneklund.minecraft.util.Log.GPU;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_SIZE;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_TYPE;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformName;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformsi;
import static org.lwjgl.system.MemoryStack.stackPush;

import com.beneklund.minecraft.util.EngineStats;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * A linked GLSL program built from one vertex and one fragment stage.
 *
 * <p>The constructor compiles both stages and links them, so an instance is always a runnable
 * program; any compile or link failure logs the driver's info log to {@code GPU} and throws.
 * GL splits the build the way a C toolchain does: {@code glCompileShader} turns each stage into
 * an object, {@code glLinkProgram} resolves the interface between them (vertex outputs to fragment
 * inputs, uniforms shared by name) into one executable. The stage objects are kept only so
 * {@link #delete()} can release them.
 *
 * <p>After linking, the program's active uniforms are enumerated once and cached by name with
 * their location and GL type. Every setter looks up that cache, so a uniform the driver optimised
 * away is a silent no-op, matching GL's own rule that location {@code -1} is ignored. Arrays are
 * expanded to one entry per element; see {@link #link()}.
 *
 * <p>All {@code set*} calls write into the program currently bound with {@link #use()}, because
 * {@code glUniform*} targets the active program and takes no program argument.
 *
 * <p>Lifecycle: construct, {@link #use()} and set uniforms each frame, {@link #delete()} on
 * shutdown. Shader hot reload builds a new instance.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Shader_Compilation">OpenGL Wiki: Shader
 *     Compilation</a>
 * @see <a href="https://wikis.khronos.org/opengl/Uniform_(GLSL)">OpenGL Wiki: Uniform
 *     (GLSL)</a>
 * @see <a href="https://registry.khronos.org/OpenGL/specs/gl/glspec33.core.pdf">OpenGL 3.3 Core
 *     Profile, section 2.11: Vertex Shaders</a>
 */
public final class GlShader {
    private int programId;
    private final String vertexShaderSource;
    private final String fragmentShaderSource;
    private int vertexShader;
    private int fragmentShader;
    // -1 rather than 0: frame ordinals start at 1, and a program must upload once before it can
    // skip. A reloaded program is a new GlShader, so this resets itself on F5.
    private long uniformsUploadedForFrame = -1;

    private final Map<String, ActiveUniform> activeUniforms = new HashMap<>();

    private record ActiveUniform(String name, int location, int type) {}

    /**
     * Compiles and links a program from GLSL source.
     *
     * @throws RuntimeException if either stage fails to compile or the program fails to link;
     *     the driver's info log is written to the {@code GPU} logger first
     */
    public GlShader(String vertexShaderSource, String fragmentShaderSource) {
        this.vertexShaderSource = vertexShaderSource;
        this.fragmentShaderSource = fragmentShaderSource;
        compile();
        link();
    }

    /**
     * Makes this the active program for subsequent draws and {@code set*} calls.
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glUseProgram.xhtml">
     *     glUseProgram</a>
     */
    public void use() {
        glUseProgram(programId);
    }

    /**
     * Uploads the frame-wide uniforms this program declares, at most once per frame.
     *
     * <p>Only active uniforms of type {@code float}, {@code vec3} and {@code mat4} are candidates.
     * A candidate with no entry in {@code uniforms} is skipped: that absence marks it as a per-draw
     * uniform such as {@code uModel}, which the caller sets itself. Calling this on every draw is
     * safe because a repeat call with the same {@code frame} returns immediately.
     *
     * @param frame the render loop's frame counter, starting at 1
     * @param uniforms frame-wide values keyed by GLSL uniform name, array elements as {@code
     *     name[i]}
     */
    public void apply(long frame, Map<String, UniformValue<?>> uniforms) {
        if (frame == uniformsUploadedForFrame) return;
        uniformsUploadedForFrame = frame;

        for (ActiveUniform uniform : activeUniforms.values()) {
            if (!drivenByFrameUniforms(uniform.type())) continue;
            UniformValue<?> value = uniforms.get(uniform.name());
            // Not an error. A uniform the frame map has no entry for is how a per-call uniform is
            // told apart from a frame one - uModel is declared by both programs and supplied by
            // neither map, because submit() sets it per draw.
            if (value == null) continue;
            upload(uniform.location(), value);
        }
    }

    /** The GL program name, for binding outside this wrapper. */
    public int getProgramId() {
        return programId;
    }

    /** Sets an {@code int} or {@code sampler*} uniform; samplers take a texture unit index. */
    public void setInt(String name, int value) {
        int location = location(name);
        if (location < 0) return;
        glUniform1i(location, value);
        EngineStats.countUniformUpload();
    }

    public void setFloat(String name, float value) {
        int location = location(name);
        if (location < 0) return;
        glUniform1f(location, value);
        EngineStats.countUniformUpload();
    }

    public void setVec2(String name, float x, float y) {
        int location = location(name);
        if (location < 0) return;
        glUniform2f(location, x, y);
        EngineStats.countUniformUpload();
    }

    public void setVec3(String name, Vector3f vec3) {
        int location = location(name);
        if (location < 0) return;
        glUniform3f(location, vec3.x(), vec3.y(), vec3.z());
        EngineStats.countUniformUpload();
    }

    /**
     * Sets a {@code mat4} uniform.
     *
     * <p>JOML and GLSL both store matrices column-major, so the upload passes {@code transpose =
     * false} and {@link Matrix4f#get(FloatBuffer)} writes the 16 floats in the order GL reads them.
     * The buffer comes from LWJGL's {@link MemoryStack}, so a per-frame upload allocates nothing on
     * the Java heap.
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glUniform.xhtml">
     *     glUniform</a>
     * @see <a href="https://github.com/LWJGL/lwjgl3-wiki/wiki/1.3.-Memory-FAQ">LWJGL Memory FAQ</a>
     */
    public void setMatrix4(String name, Matrix4f matrix) {
        int location = location(name);
        if (location < 0) return;

        try (MemoryStack stack = stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            glUniformMatrix4fv(location, false, buffer);
            EngineStats.countUniformUpload();
        }
    }

    private static void upload(int location, UniformValue<?> value) {
        switch (value) {
            case UniformValue.F f -> glUniform1f(location, f.value());
            case UniformValue.V3 v ->
                glUniform3f(location, v.value().x(), v.value().y(), v.value().z());
            case UniformValue.M4 m -> {
                try (MemoryStack stack = stackPush()) {
                    FloatBuffer buffer = stack.mallocFloat(16);
                    m.value().get(buffer);
                    glUniformMatrix4fv(location, false, buffer);
                }
            }
        }
        EngineStats.countUniformUpload();
    }

    private static boolean drivenByFrameUniforms(int glType) {
        return glType == GL_FLOAT || glType == GL_FLOAT_VEC3 || glType == GL_FLOAT_MAT4;
    }

    private int location(String name) {
        ActiveUniform uniform = activeUniforms.get(name);
        return uniform == null ? -1 : uniform.location();
    }

    /** Deletes the program and both stage objects. */
    public void delete() {
        glDeleteProgram(programId);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private void compile() {
        vertexShader = glCreateShader(GL_VERTEX_SHADER);
        fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(vertexShader);
        glCompileShader(fragmentShader);
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            GPU.error(glGetShaderInfoLog(vertexShader));
            throw new RuntimeException("Failed to compile() vertex shader.");
        }
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            GPU.error(glGetShaderInfoLog(fragmentShader));
            throw new RuntimeException("Failed to compile() fragment shader.");
        }
    }

    /**
     * Links the program and caches every active uniform, one entry per array element.
     *
     * <p>GL reports an array uniform as a single active uniform named {@code uFoo[0]} with a size,
     * and each element has its own location queried by {@code uFoo[i]}.
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGetActiveUniform.xhtml">
     *     glGetActiveUniform</a>
     */
    private void link() {
        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            GPU.error(glGetProgramInfoLog(programId));
            throw new RuntimeException("Failed to link() shader program");
        }

        int count = glGetProgrami(programId, GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < count; i++) {
            String name = glGetActiveUniformName(programId, i);
            int uniformType = glGetActiveUniformsi(programId, i, GL_UNIFORM_TYPE);
            int size = glGetActiveUniformsi(programId, i, GL_UNIFORM_SIZE);

            /*
             * An array arrives as ONE active uniform. GL reports `uFoo[3]` as a single entry named
             * "uFoo[0]" with size 3 — so registering only what the enumeration hands back leaves
             * elements 1 and up with no entry at all, and every lookup for them silently misses.
             *
             * That is not a hypothetical. chunk.frag's per-cascade uLightViewProj, uShadowBias and
             * uCascadeSplit were uploaded for element 0 only; elements 1 and 2 kept their default
             * of zero, so cascadeFor compared against a split of 0, reported "no cascade" for
             * anything past the first split, and shadows silently stopped at 28 blocks. Nothing
             * warned, because element 0 WAS supplied and the other elements were never asked for.
             *
             * So expand arrays here: one entry per element, each with its own location.
             */
            String base = name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;
            for (int element = 0; element < size; element++) {
                String elementName = size == 1 ? base : base + "[" + element + "]";
                int location = glGetUniformLocation(programId, elementName);
                if (location < 0) continue;
                activeUniforms.put(elementName, new ActiveUniform(elementName, location, uniformType));
            }
        }
        GPU.debug("program {} declares uniform(s): {}", programId, activeUniforms.keySet());
    }
}
