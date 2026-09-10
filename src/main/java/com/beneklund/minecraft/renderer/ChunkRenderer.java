package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.util.EngineStats;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class ChunkRenderer implements IRenderable {
    private static final String VERT_PATH = "/shaders/chunk.vert";
    private static final String FRAG_PATH = "/shaders/chunk.frag";
    private static final String SHADOW_VERT_PATH = "/shaders/shadow.vert";
    private static final String SHADOW_FRAG_PATH = "/shaders/shadow.frag";

    private final RenderWorld renderWorld;
    private final TextureAtlas atlas;

    // Whether the shadow pass runs at all. Without it this class builds a SHADOW DrawCall per
    // loaded chunk per frame for a pass that returns immediately — measured at ~3,000 objects a
    // frame, allocated and dropped, which is GC pressure paid for nothing.
    private final RenderFeatures features;
    // Instance, not static final: a static initializer runs at class-load, which isn't
    // guaranteed to be after the GL context exists.
    private final ShaderProgram chunkShader;
    private final ShaderProgram shadowShader;

    /*
     * Pushed once per frame from Game, beside renderer.setSunDirection.
     *
     * Pushed rather than pulled from the DayNightCycle because the caster test and the light
     * matrix have to agree on where the sun is within a frame. cycle.advance() runs early in the
     * tick and getDrawCalls runs late inside drawScene; a second read could legally differ, and a
     * chunk rejected against a sun the shadow map was not rendered from is a shadow that pops.
     *
     * Starts on the horizon, the most conservative reach, so the first frame cannot under-draw
     * before Game has pushed anything.
     */
    private final Vector3f sunDirection = new Vector3f(0.0f, 0.0f, 1.0f);

    public void setSunDirection(Vector3fc sunDirection) {
        this.sunDirection.set(sunDirection);
    }

    public ChunkRenderer(RenderWorld renderWorld, TextureAtlas atlas, RenderFeatures features) {
        this.renderWorld = renderWorld;
        this.atlas = atlas;
        this.features = features;
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
        // Hoisted out of the loop: it cannot change within a frame, and testing it per entry was
        // the point of the measurement below, not a saving.
        boolean shadows = features.sunShadows();
        for (RenderWorld.Entry entry : renderWorld.getEntries()) {
            EngineStats.countChunkConsidered();
            /*
             * Casters are collected before the frustum test on purpose — a chunk behind the camera
             * still casts into the view. But when the shadow pass is off, every one of these is an
             * allocation for a pass that returns immediately at the top of drawShadowPass.
             *
             * Measured while flying at render distance 32: ~3,000 SHADOW DrawCalls a frame against
             * ~900 opaque ones, submitting zero vertices. JFR put ChunkRenderer.getDrawCalls among
             * the top allocation sites in the process, and the GC that follows lands on the main
             * thread, which is where the frame is.
             */
            if (shadows && entry.opaqueMesh() != null) {
                int cascades = cascadeMaskFor(entry.bounds(), eye, sunDirection);
                if (cascades != 0) {
                    result.add(new DrawCall(
                            entry.opaqueMesh(), entry.model(), shadowShader, atlas, RenderPass.SHADOW, cascades));
                }
            }
            if (!frustum.isVisible(entry.bounds())) continue;
            if (entry.opaqueMesh() != null) {
                result.add(new DrawCall(entry.opaqueMesh(), entry.model(), chunkShader, atlas, RenderPass.OPAQUE));
                EngineStats.countChunkDrawn();
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
    /*
     * Which shadow cascades this box can cast into, as a bitmask. Package-private rather than
     * private so ChunkRendererTest can pin it: it takes plain vectors, touches no GL and no
     * renderer state, and the draw-call counts predicted for a still camera rest on it ignoring
     * the frustum entirely.
     *
     * The sun is a parameter rather than the field so the test can sweep it. Which cascades a
     * chunk can cast into depends on where the light comes from and how low it is, not on
     * distance alone - see upSunFraction below and ShadowCamera.shadowReach.
     */
    static int cascadeMaskFor(AABB bounds, Vector3f eye, Vector3f sunDirection) {
        float dx = Math.max(0.0f, Math.max(bounds.minX() - eye.x, eye.x - bounds.maxX()));
        float dz = Math.max(0.0f, Math.max(bounds.minZ() - eye.z, eye.z - bounds.maxZ()));
        float distanceSquared = dx * dx + dz * dz;

        float upSun = upSunFraction(bounds, eye, sunDirection);

        int mask = 0;
        for (int cascade = 0; cascade < ShadowCamera.cascadeCount(); cascade++) {
            float radius = ShadowCamera.casterRadius(cascade, sunDirection, upSun);
            if (distanceSquared <= radius * radius) mask |= 1 << cascade;
        }
        return mask;
    }

    /*
     * How much this chunk sits on the side the light comes from, 0 to 1.
     *
     * Bearing comes from the chunk's centre, not from the nearest corner the distance above is
     * measured to: a 16-block box straddling the eye has no meaningful bearing from a corner. A
     * chunk sitting on the eye returns 1, which costs nothing because it is inside every box
     * already.
     */
    private static float upSunFraction(AABB bounds, Vector3f eye, Vector3f sunDirection) {
        float sx = sunDirection.x();
        float sz = sunDirection.z();
        float sunLength = (float) Math.sqrt(sx * sx + sz * sz);
        // Sun overhead: no side to be on, and shadowReach is ~0 anyway, so the answer is unused.
        if (sunLength < 1.0e-4f) return 0.0f;

        float ox = (bounds.minX() + bounds.maxX()) * 0.5f - eye.x;
        float oz = (bounds.minZ() + bounds.maxZ()) * 0.5f - eye.z;
        float offsetLength = (float) Math.sqrt(ox * ox + oz * oz);
        if (offsetLength < 1.0e-4f) return 1.0f;

        return Math.max(0.0f, (ox * sx + oz * sz) / (offsetLength * sunLength));
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
