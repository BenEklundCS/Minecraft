package com.beneklund.minecraft.platform.graphics;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static org.lwjgl.opengl.GL20.*;

public class GlShader {
    private int programId;
    private final String vertexShaderSource;
    private final String fragmentShaderSource;
    private int vertexShader;
    private int fragmentShader;

    public GlShader(String vertexShaderSource, String fragmentShaderSource) {
        this.vertexShaderSource = vertexShaderSource;
        this.fragmentShaderSource = fragmentShaderSource;
        compile();
        link();
    }

    private void compile() {
        this.vertexShader = glCreateShader(GL_VERTEX_SHADER);
        this.fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(this.vertexShader, vertexShaderSource);
        glShaderSource(this.fragmentShader, fragmentShaderSource);
        glCompileShader(this.vertexShader);
        glCompileShader(this.fragmentShader);
        if (glGetShaderi(this.vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            LOGGER.error(glGetShaderInfoLog(this.vertexShader));
            throw new RuntimeException("Failed to compile() vertex shader.");
        }
        if (glGetShaderi(this.fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            LOGGER.error(glGetShaderInfoLog(this.fragmentShader));
            throw new RuntimeException("Failed to compile() fragment shader.");
        }
    }

    private void link() {
        this.programId = glCreateProgram();
        glAttachShader(this.programId, this.vertexShader);
        glAttachShader(this.programId, this.fragmentShader);
        glLinkProgram(this.programId);
        if (glGetProgrami(this.programId, GL_LINK_STATUS) == GL_FALSE) {
            LOGGER.error(glGetProgramInfoLog(programId));
            throw new RuntimeException("Failed to link() shader program");
        }
    }

    public void use() {
        glUseProgram(this.programId);
    }

    public int getProgramId() {
        return this.programId;
    }

    public void delete() {
        glDeleteProgram(this.programId);
        glDeleteShader(this.vertexShader);
        glDeleteShader(this.fragmentShader);
    }
}
