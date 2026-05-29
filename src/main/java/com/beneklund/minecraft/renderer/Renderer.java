package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.GlBuffer;
import com.beneklund.minecraft.platform.graphics.GlShader;
import com.beneklund.minecraft.platform.graphics.GlVertexArray;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;

public class Renderer {
    private final GlShader shader;
    private final GlVertexArray vao;
    private final GlBuffer vbo;

    public Renderer(String vertexSource, String fragmentSource, float[] vertices) {
        this.shader = new GlShader(vertexSource, fragmentSource);
        this.vbo = new GlBuffer();
        this.vao = new GlVertexArray();

        this.vao.bind();
        this.vbo.upload(vertices, GlBuffer.Target.ARRAY);
        this.vao.attribPointer(0, 3, 12, 0L);
        this.vao.unbind();
    }

    public void render() {
        this.shader.use();
        this.vao.bind();
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }
}
