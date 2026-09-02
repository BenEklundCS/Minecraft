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

/*
 * Wraps an OpenGL shader program. Compile and link both happen in the constructor
 * so you can never get a half-built shader - it either works or throws.
 *
 * OpenGL treats this like a compiler + linker: compile() turns GLSL source into
 * GPU machine code for each stage, link() connects them into a runnable program.
 * After linking, the individual shader objects are dead weight - same idea as
 * deleting .o files after a successful build.
 *
 * vertexShader and fragmentShader remain as fields only so delete() can clean them up.
 *
 * Lifecycle: new -> use() each frame -> delete() on shutdown.
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

    public GlShader(String vertexShaderSource, String fragmentShaderSource) {
        this.vertexShaderSource = vertexShaderSource;
        this.fragmentShaderSource = fragmentShaderSource;
        compile();
        link();
    }

    public void use() {
        glUseProgram(programId);
    }

    // Uploads every driven uniform this program declares. Safe to call on every draw: frame is the
    // render-loop counter, and a second call with the same value returns immediately, so the guard
    // below is what makes it once per program per frame rather than the caller remembering to.
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

    public int getProgramId() {
        return programId;
    }

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

    // Uploads a 4x4 matrix into a mat4 uniform. The program must be bound (use()) first - glUniform*
    // always writes into the currently-active program, not the one named here.
    public void setMatrix4(String name, Matrix4f matrix) {
        int location = location(name);
        if (location < 0) return; // uniform doesn't exist or got stripped as unused; nothing to set

        // JOML and OpenGL are both column-major, so no transpose (false). The GPU wants the 16
        // floats laid out contiguously; matrix.get() writes them in column-major order. We alloc
        // on the LWJGL stack so this per-frame upload doesn't churn the GC heap.
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
