package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.util.AABB;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public class ChunkRenderer implements IRenderable {
    private static final String VERT_PATH = "/shaders/chunk.vert";
    private static final String FRAG_PATH = "/shaders/chunk.frag";
    private static final String SHADOW_VERT_PATH = "/shaders/shadow.vert";
    private static final String SHADOW_FRAG_PATH = "/shaders/shadow.frag";

    private final RenderWorld renderWorld;
    private final TextureAtlas atlas;
    // Instance, not static final: a static initializer runs at class-load, which isn't
    // guaranteed to be after the GL context exists.
    private final ShaderProgram chunkShader;
    private final ShaderProgram shadowShader;

    public ChunkRenderer(RenderWorld renderWorld, TextureAtlas atlas) {
        this.renderWorld = renderWorld;
        this.atlas = atlas;
        // Constructing directly rather than calling reload() — a shader that won't compile at
        // startup should stop the game with the GLSL error, and reload() has nothing to fall
        // back to before this assignment anyway.
        chunkShader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        shadowShader = new ShaderProgram(SHADOW_VERT_PATH, SHADOW_FRAG_PATH);
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        Frustum frustum = new Frustum(camera.getViewProjectionMatrix());
        Vector3f eye = camera.getPosition();
        List<DrawCall> result = new ArrayList<>();
        for (RenderWorld.Entry entry : renderWorld.getEntries()) {
            if (entry.opaqueMesh() != null) {
                int cascades = cascadeMaskFor(entry.bounds(), eye);
                if (cascades != 0) {
                    result.add(new DrawCall(
                            entry.opaqueMesh(), entry.model(), shadowShader, atlas, RenderPass.SHADOW, cascades));
                }
            }
            if (!frustum.isVisible(entry.bounds())) continue;
            if (entry.opaqueMesh() != null) {
                result.add(new DrawCall(entry.opaqueMesh(), entry.model(), chunkShader, atlas, RenderPass.OPAQUE));
            }
            if (entry.transparentMesh() != null) {
                result.add(new DrawCall(
                        entry.transparentMesh(), entry.model(), chunkShader, atlas, RenderPass.TRANSPARENT));
            }
        }
        return result;
    }

    /*
     * Which cascades this chunk can cast into, one bit each.
     *
     * Horizontal distance from the eye to the nearest point of the chunk's box. Vertical extent is
     * ignored: the sun's box spans the full world height, so a chunk is either within horizontal
     * reach of a cascade or it is not.
     *
     * The near cascade covers a much smaller area, so most loaded chunks fail its test and are
     * never submitted to it — which is the saving that pays for rendering the scene twice.
     */
    private static int cascadeMaskFor(AABB bounds, Vector3f eye) {
        float dx = Math.max(0.0f, Math.max(bounds.minX() - eye.x, eye.x - bounds.maxX()));
        float dz = Math.max(0.0f, Math.max(bounds.minZ() - eye.z, eye.z - bounds.maxZ()));
        float distanceSquared = dx * dx + dz * dz;

        int mask = 0;
        for (int cascade = 0; cascade < ShadowCamera.cascadeCount(); cascade++) {
            float radius = ShadowCamera.casterRadius(cascade);
            if (distanceSquared <= radius * radius) mask |= 1 << cascade;
        }
        return mask;
    }

    @Override
    public void reload() {
        chunkShader.reload();
        shadowShader.reload();
    }

    @Override
    public void delete() {
        chunkShader.delete();
        shadowShader.delete();
    }
}
