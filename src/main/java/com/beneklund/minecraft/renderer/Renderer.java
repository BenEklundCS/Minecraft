package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.platform.graphics.GlElementArrayBuffer;
import com.beneklund.minecraft.platform.graphics.GlVertexArray;
import com.beneklund.minecraft.platform.graphics.GlVertexArrayBuffer;
import org.joml.Matrix4f;

// Earlier triangle/cube renderer kept for reference. Uses an 8-float vertex format
// (pos+uv+tint, stride 32), unlike GpuMesh which uses the 10-float chunk format (stride 40).
public class Renderer {
    private final ShaderProgram shader;
    private final GlVertexArray vao;
    private final GlVertexArrayBuffer vbo;
    private final GlElementArrayBuffer ebo;
    private final TextureAtlas atlas;
    private final int indexCount;

    public Renderer(String vertPath, String fragPath, float[] vertices, int[] indices, TextureAtlas atlas) {
        this.shader = new ShaderProgram(vertPath, fragPath);
        this.vbo = new GlVertexArrayBuffer();
        this.ebo = new GlElementArrayBuffer();
        this.vao = new GlVertexArray();
        this.indexCount = indices.length;
        this.atlas = atlas;

        this.vao.bind();
        this.vbo.upload(vertices);
        this.ebo.upload(indices);
        this.vao.attribPointer(0, 3, 32, 0L); // position: 3 floats, stride 32 bytes
        this.vao.attribPointer(1, 2, 32, 12L); // uv: 2 floats, offset 12 bytes (3*4)
        this.vao.attribPointer(2, 3, 32, 20L); // tint: 3 floats, offset 20 bytes (5*4)
        this.vao.unbind();
    }

    public void render(Matrix4f model, Matrix4f view, Matrix4f projection) {
        this.shader.bind();
        this.shader.setUniformMat4("model", model);
        this.shader.setUniformMat4("view", view);
        this.shader.setUniformMat4("projection", projection);
        glActiveTexture(GL_TEXTURE0);
        this.atlas.bind();
        this.vao.bind();
        glDrawElements(GL_TRIANGLES, this.indexCount, GL_UNSIGNED_INT, 0L);
    }

    public void delete() {
        this.shader.delete();
        this.vao.delete();
        this.vbo.delete();
        this.ebo.delete();
    }
}
