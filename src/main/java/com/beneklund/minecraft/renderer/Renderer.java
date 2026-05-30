package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.platform.graphics.GlShader;
import com.beneklund.minecraft.platform.graphics.GlTexture;
import com.beneklund.minecraft.platform.graphics.GlVertexArray;
import com.beneklund.minecraft.platform.graphics.GlVertexArrayBuffer;
import org.joml.Matrix4f;

public class Renderer {
    private final GlShader shader;
    private final GlVertexArray vao;
    private final GlVertexArrayBuffer vbo;
    private final GlTexture texture;

    public Renderer(String vertexSource, String fragmentSource, float[] vertices, String texturePath) {
        this.shader = new GlShader(vertexSource, fragmentSource);
        this.vbo = new GlVertexArrayBuffer();
        this.vao = new GlVertexArray();
        this.texture = new GlTexture();

        this.texture.load(texturePath);
        this.texture.upload();

        this.vao.bind();
        this.vbo.upload(vertices);
        this.vao.attribPointer(0, 3, 20, 0L);   // position: 3 floats, stride 20 bytes
        this.vao.attribPointer(1, 2, 20, 12L);  // uv: 2 floats, offset 12 bytes (3*4)
        this.vao.unbind();
    }

    public void render(Matrix4f view, Matrix4f projection) {
        this.shader.use();
        // Must be after use() - glUniform* writes into the bound program. Upload every frame:
        // an unset mat4 uniform is the zero matrix, which would collapse all geometry to a point.
        this.shader.setMatrix4("view", view);
        this.shader.setMatrix4("projection", projection);
        glActiveTexture(GL_TEXTURE0);
        this.texture.bind();
        this.vao.bind();
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }
}