package com.beneklund.minecraft.renderer;

import static com.beneklund.minecraft.util.Log.RENDER;
import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.util.Color;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

// Collects DrawCalls from all Renderables each frame and submits them to the GPU,
// opaque pass first then transparent pass (see draw()).
public class Renderer {
    private final List<IRenderable> registered;
    private Color fogColor;
    private Vector3f fogColorVec;
    private Vector3f horizonColorVec;
    private Vector3f zenithColorVec;
    private final Vector2f fogRange;
    private float skyBrightness;

    private final Matrix4f viewRotation = new Matrix4f();
    private final Matrix4f invViewProj = new Matrix4f();

    public Renderer(List<IRenderable> registered, Color fogColor, Vector2f fogRange) {
        this.registered = registered;
        this.fogColor = fogColor;
        fogColorVec = fogColor.toRgbVec3();
        this.fogRange = fogRange;
    }

    public void delete() {
        RENDER.debug("deleting {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.delete();
    }

    public void reloadAll() {
        RENDER.info("reloading {} renderable(s)", registered.size());
        for (IRenderable r : registered) r.reload();
    }

    public void setSkyBrightness(float skyBrightness) {
        this.skyBrightness = skyBrightness;
        fogColor = Color.FOG.scale(skyBrightness);
        fogColorVec = fogColor.toRgbVec3();
        horizonColorVec = Color.SKY_HORIZON.scale(skyBrightness).toRgbVec3();
        zenithColorVec = Color.SKY_ZENITH.scale(skyBrightness).toRgbVec3();
    }

    // The clear color has to match the fog color exactly or the horizon shows a seam where
    // fog stops and the cleared background starts. Renderer owns the tinting, so the window
    // reads it back from here rather than computing its own.
    public Color fogColor() {
        return fogColor;
    }

    public void draw(Camera camera) {
        viewRotation.set(camera.getViewMatrix()).setTranslation(0, 0, 0);
        invViewProj.set(camera.getProjectionMatrix()).mul(viewRotation).invert();

        // Collect every renderable's calls first, then draw by pass. Gathering across all
        // renderables means transparent geometry blends against the full opaque scene, not
        // just whatever opaque calls happened to come before it in the same renderable.
        // NOTE: getDrawCalls must not set GL state — Renderer owns it entirely.
        List<DrawCall> calls = new ArrayList<>();
        for (IRenderable renderable : registered) calls.addAll(renderable.getDrawCalls(camera));

        // Guarded because this runs every frame — without the check we'd walk the call list three
        // extra times per frame just to build a message nobody is listening to.
        if (RENDER.isTraceEnabled()) {
            RENDER.trace(
                    "{} draw call(s): {} opaque, {} transparent, {} hud",
                    calls.size(),
                    countPass(calls, RenderPass.OPAQUE),
                    countPass(calls, RenderPass.TRANSPARENT),
                    countPass(calls, RenderPass.HUD));
        }

        // Opaque pass: full depth test + write, no blending.
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);
        for (DrawCall call : calls) if (call.pass() == RenderPass.OPAQUE) submit(call, camera);

        // Transparent pass: depth test on so water is occluded by terrain, but depth write
        // off so transparent surfaces behind other transparent surfaces still draw.
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (DrawCall call : calls) if (call.pass() == RenderPass.TRANSPARENT) submit(call, camera);

        // HUD pass: drawn last over everything, no depth test, blending on for alpha.
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (DrawCall call : calls) if (call.pass() == RenderPass.HUD) submit(call, camera);

        // Restore sane defaults for the next frame.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private static long countPass(List<DrawCall> calls, RenderPass pass) {
        return calls.stream().filter(c -> c.pass() == pass).count();
    }

    private void submit(DrawCall call, Camera camera) {
        if (call.atlas().isPresent()) call.atlas().get().bind();
        else glBindTexture(GL_TEXTURE_2D, 0);
        call.shader().bind();
        call.shader().setUniformMat4("uView", camera.getViewMatrix());
        call.shader().setUniformMat4("uProjection", camera.getProjectionMatrix());
        call.shader().setUniformMat4("uModel", call.transform());
        call.shader().setUniformVec3("uFogColor", fogColorVec);
        call.shader().setUniformFloat("uFogStart", fogRange.x);
        call.shader().setUniformFloat("uFogEnd", fogRange.y);
        call.shader().setUniformFloat("uSkyBrightness", skyBrightness);
        call.shader().setUniformVec3("uHorizonColor", horizonColorVec);
        call.shader().setUniformVec3("uZenithColor", zenithColorVec);
        call.shader().setUniformMat4("uInvViewProj", invViewProj);
        call.mesh().render();
    }
}
