package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

// Holds the VAO/VBO/EBO for one uploaded chunk mesh. Must only be created and deleted
// on the main (GL) thread. Workers produce ChunkMeshData; this class is the GL result.
public class GpuMesh {

    private final GlVertexArray vao;
    private final IGlVertexArrayBuffer vbo;
    private final IGlElementArrayBuffer ebo;
    private final int indexCount;

    // Vertex format: 10 floats per vertex — x, y, z, u, v, ao, faceId, r, g, b. Stride = 40 bytes.
    public GpuMesh(float[] vertices, int[] indices) {
        if (!Thread.currentThread().getName().equals("main"))
            throw new IllegalStateException("GpuMesh must be created on the main thread, was: " + Thread.currentThread().getName());
        this.vao = new GlVertexArray();
        this.vbo = new IGlVertexArrayBuffer();
        this.ebo = new IGlElementArrayBuffer();
        this.indexCount = indices.length;

        vao.bind();
        vbo.upload(vertices);
        ebo.upload(indices); // must happen inside vao.bind() — EBO binding is part of VAO state
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
