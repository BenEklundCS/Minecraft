package com.beneklund.minecraft.platform.graphics;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

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
public class GlShader {
    private int programId;
    private final String vertexShaderSource;
    private final String fragmentShaderSource;
    private int vertexShader;
    private int fragmentShader;

    // glGetUniformLocation is a string lookup into the linked program - cache it so we're not
    // doing it every frame for the same name. -1 means "no such active uniform" (see setMatrix4).
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public GlShader(String vertexShaderSource, String fragmentShaderSource) {
        this.vertexShaderSource = vertexShaderSource;
        this.fragmentShaderSource = fragmentShaderSource;
        compile();
        link();
    }

    public void use() {
        glUseProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }

    // Uploads a 4x4 matrix into a mat4 uniform. The program must be bound (use()) first - glUniform*
    // always writes into the currently-active program, not the one named here.
    public void setFloat(String name, float value) {
        int location = location(name);
        if (location < 0) return;
        glUniform1f(location, value);
    }

    public void setVec3(String name, Vector3f vec3) {
        int location = location(name);
        if (location < 0) return;
        glUniform3f(location, vec3.x(), vec3.y(), vec3.z());
    }

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

    private int location(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
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
            LOGGER.error(glGetShaderInfoLog(vertexShader));
            throw new RuntimeException("Failed to compile() vertex shader.");
        }
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            LOGGER.error(glGetShaderInfoLog(fragmentShader));
            throw new RuntimeException("Failed to compile() fragment shader.");
        }
    }

    private void link() {
        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            LOGGER.error(glGetProgramInfoLog(programId));
            throw new RuntimeException("Failed to link() shader program");
        }
    }
}
