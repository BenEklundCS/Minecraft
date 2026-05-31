package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.platform.graphics.GpuMesh;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import org.joml.Matrix4f;

// Binds the chunk shader + atlas and renders all uploaded meshes from RenderWorld each frame.
public class ChunkRenderer {

    private final ShaderProgram shader;
    private final TextureAtlas atlas;

    public ChunkRenderer(TextureAtlas atlas) {
        this.shader = new ShaderProgram("/shaders/chunk.vert", "/shaders/chunk.frag");
        this.atlas = atlas;
    }

    // View and projection are set once; each chunk gets its own model matrix translated to its world origin.
    // Chunk verts are in local space (0-15), so model = translate(chunkX * SIZE_XZ, 0, chunkZ * SIZE_XZ).
    public void render(RenderWorld renderWorld, Matrix4f view, Matrix4f projection) {
        shader.bind();
        shader.setUniformMat4("uView", view);
        shader.setUniformMat4("uProjection", projection);
        glActiveTexture(GL_TEXTURE0);
        atlas.bind();
        for (var entry : renderWorld.getEntries()) {
            ChunkPos pos = entry.getKey();
            GpuMesh mesh = entry.getValue();
            Matrix4f model = new Matrix4f().translation(
                    pos.x() * Chunk.SIZE_XZ,
                    0,
                    pos.z() * Chunk.SIZE_XZ);
            shader.setUniformMat4("uModel", model);
            mesh.render();
        }
    }

    public void delete() {
        shader.delete();
    }
}
