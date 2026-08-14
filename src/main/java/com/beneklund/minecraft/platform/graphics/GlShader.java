package com.beneklund.minecraft.platform.graphics;

import static com.beneklund.minecraft.util.Log.GPU;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_TYPE;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformName;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformsi;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    // tracking of complained about uniforms so a supplier logs once
    private final Set<String> warned = new HashSet<>();
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

    public void apply(Map<String, UniformValue<?>> frame, Map<String, UniformValue<?>> call) {
        for (ActiveUniform uniform : activeUniforms.values()) {
            if (!drivenByFrameUniforms(uniform.type())) continue;
            UniformValue<?> value = call.get(uniform.name());
            if (value == null) value = frame.get(uniform.name());
            if (value == null) {
                if (warned.add(uniform.name())) {
                    GPU.warn("program {} declares {} but nothing supplies it", programId, uniform.name());
                }
                continue;
            }
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
    }

    public void setFloat(String name, float value) {
        int location = location(name);
        if (location < 0) return;
        glUniform1f(location, value);
    }

    public void setVec2(String name, float x, float y) {
        int location = location(name);
        if (location < 0) return;
        glUniform2f(location, x, y);
    }

    public void setVec3(String name, Vector3f vec3) {
        int location = location(name);
        if (location < 0) return;
        glUniform3f(location, vec3.x(), vec3.y(), vec3.z());
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
            int location = glGetUniformLocation(programId, name);
            if (location < 0) continue;
            activeUniforms.put(name, new ActiveUniform(name, location, uniformType));
        }
        GPU.debug("program {} declares uniform(s): {}", programId, activeUniforms.keySet());
    }
}
