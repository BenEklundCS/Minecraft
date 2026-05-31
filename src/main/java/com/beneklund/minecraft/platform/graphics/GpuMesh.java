package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

// Holds the VAO/VBO/EBO for one uploaded chunk mesh. Must only be created and deleted
// on the main (GL) thread. Workers produce ChunkMeshData; this class is the GL result.
public class GpuMesh {

    private final GlVertexArray vao;
    private final GlVertexArrayBuffer vbo;
    private final GlElementArrayBuffer ebo;
    private final int indexCount;

    // Vertex format: 10 floats per vertex — x, y, z, u, v, ao, faceId, r, g, b. Stride = 40 bytes.
    public GpuMesh(float[] vertices, int[] indices) {
        this.vao = new GlVertexArray();
        this.vbo = new GlVertexArrayBuffer();
        this.ebo = new GlElementArrayBuffer();
        this.indexCount = indices.length;

        vao.bind();
        vbo.upload(vertices);
        ebo.upload(indices);
        vao.attribPointer(0, 3, 40, 0L); // position: xyz
        vao.attribPointer(1, 2, 40, 12L); // uv
        vao.attribPointer(2, 1, 40, 20L); // ao
        vao.attribPointer(3, 1, 40, 24L); // faceId
        vao.attribPointer(4, 3, 40, 28L); // tint: rgb
        vao.unbind();
    }

    // Binds the VAO and issues the draw call. Caller binds the shader and texture first.
    public void render() {
        vao.bind();
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
    }

    public void delete() {
        vao.delete();
        vbo.delete();
        ebo.delete();
    }
}
