package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.platform.graphics.GpuMesh;
import org.joml.Matrix4f;

// Binds the chunk shader + atlas and draws a single GpuMesh each frame.
// Temporary single-mesh holder for the Phase 10 checkpoint; Phase 13 replaces this
// with the Renderable/DrawCall pipeline that handles all loaded chunks.
public class ChunkRenderer {

    private final ShaderProgram shader;
    private final GpuMesh mesh;
    private final TextureAtlas atlas;

    public ChunkRenderer(GpuMesh mesh, TextureAtlas atlas) {
        this.shader = new ShaderProgram("/shaders/chunk.vert", "/shaders/chunk.frag");
        this.mesh = mesh;
        this.atlas = atlas;
    }

    public void render(Matrix4f view, Matrix4f projection) {
        shader.bind();
        shader.setUniformMat4("uModel", new Matrix4f().identity());
        shader.setUniformMat4("uView", view);
        shader.setUniformMat4("uProjection", projection);
        glActiveTexture(GL_TEXTURE0);
        atlas.bind();
        mesh.render();
    }

    public void delete() {
        shader.delete();
        mesh.delete();
    }
}
