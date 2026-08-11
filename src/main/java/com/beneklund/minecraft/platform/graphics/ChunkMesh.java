package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

// Holds the VAO/VBO/EBO for one uploaded mesh. Must only be created and deleted
// on the main (GL) thread. Workers produce ChunkMeshData; this class is the GL result.
public class ChunkMesh implements Mesh {
    private static final VertexFormat vf = VertexFormat.CHUNK;
    private final GlVertexArray vao;
    private final IGlVertexArrayBuffer vbo;
    private final IGlElementArrayBuffer ebo;
    private final int indexCount;

    public ChunkMesh(float[] vertices, int[] indices) {
        if (!Thread.currentThread().getName().equals("main"))
            throw new IllegalStateException("GpuMesh must be created on the main thread, was: "
                    + Thread.currentThread().getName());
        vf.checkVertexCount(vertices.length);
        vao = new GlVertexArray();
        vbo = new IGlVertexArrayBuffer();
        ebo = new IGlElementArrayBuffer();
        indexCount = indices.length;

        vao.bind();
        vbo.upload(vertices);
        ebo.upload(indices); // must happen inside vao.bind() — EBO binding is part of VAO state
        vf.describe(vao);
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
