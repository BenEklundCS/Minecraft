package com.beneklund.minecraft.renderer;

import static com.beneklund.minecraft.util.Log.RENDER;
import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.platform.graphics.UniformValue;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.world.PreethamSky;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Collects DrawCalls from all Renderables each frame and submits them to the GPU,
// opaque pass first then transparent pass (see draw()).
public class Renderer {
    private final List<IRenderable> registered;
    private Color fogColor;
    private Vector3f fogColorVec;
    private Vector3f sunDirection;
    private final Vector2f fogRange;
    private float skyBrightness;

    private static final float TURBIDITY = 2.5f;

    private static final float EXPOSURE = 0.115f;

    private PreethamSky sky;
    private final Vector4f horizonProbe = new Vector4f();
    private final Vector3f horizonDir = new Vector3f();

    private Vector3f coefficientA;
    private Vector3f coefficientB;
    private Vector3f coefficientC;
    private Vector3f coefficientD;
    private Vector3f coefficientE;
    private Vector3f skyZenith;
    private Vector3f skyZenithF;

    private final Matrix4f viewRotation = new Matrix4f();
    private final Matrix4f invViewProj = new Matrix4f();
    private final Matrix4f modelScratch = new Matrix4f();

    private final Map<String, UniformValue<?>> frameUniforms = new HashMap<>();

    public Renderer(List<IRenderable> registered, Color fogColor, Vector2f fogRange) {
        this.registered = registered;
        this.fogColor = fogColor;
        fogColorVec = fogColor.toRgbVec3();
        this.fogRange = fogRange;
        // Seeded so the sky uniforms are never null if a frame draws before Game.run pushes
        // the real sun position. The sky shader resolves them to a real location, so unlike
        // the chunk shader it would NPE rather than no-op.
        setSunDirection(new Vector3f(0.0f, 1.0f, 0.0f));
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
    }

    public void setSunDirection(Vector3f sunDirection) {
        this.sunDirection = sunDirection;
        sky = new PreethamSky(TURBIDITY, sunDirection);
        coefficientA = sky.coefficientA();
        coefficientB = sky.coefficientB();
        coefficientC = sky.coefficientC();
        coefficientD = sky.coefficientD();
        coefficientE = sky.coefficientE();
        skyZenith = sky.zenith();
        skyZenithF = sky.zenithF();
    }

    public Color fogColor() {
        return fogColor;
    }

    public void draw(Camera camera) {
        viewRotation.set(camera.getViewMatrix()).setTranslation(0, 0, 0);
        invViewProj.set(camera.getProjectionMatrix()).mul(viewRotation).invert();

        setUniforms(camera);
        updateFogFromSky();

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

    private void setUniforms(Camera camera) {
        frameUniforms.clear();
        frameUniforms.put("uView", new UniformValue.M4(camera.getViewMatrix()));
        frameUniforms.put("uProjection", new UniformValue.M4(camera.getProjectionMatrix()));
        frameUniforms.put("uInvViewProj", new UniformValue.M4(invViewProj));
        frameUniforms.put("uFogColor", new UniformValue.V3(fogColorVec));
        frameUniforms.put("uFogStart", new UniformValue.F(fogRange.x));
        frameUniforms.put("uFogEnd", new UniformValue.F(fogRange.y));
        frameUniforms.put("uSkyBrightness", new UniformValue.F(skyBrightness));
        frameUniforms.put("uExposure", new UniformValue.F(EXPOSURE));
        frameUniforms.put("uSunDirection", new UniformValue.V3(sunDirection));
        frameUniforms.put("uA", new UniformValue.V3(coefficientA));
        frameUniforms.put("uB", new UniformValue.V3(coefficientB));
        frameUniforms.put("uC", new UniformValue.V3(coefficientC));
        frameUniforms.put("uD", new UniformValue.V3(coefficientD));
        frameUniforms.put("uE", new UniformValue.V3(coefficientE));
        frameUniforms.put("uZenith", new UniformValue.V3(skyZenith));
        frameUniforms.put("uZenithF", new UniformValue.V3(skyZenithF));
        frameUniforms.put("uModel", new UniformValue.M4(modelScratch));
    }

    private void updateFogFromSky() {
        horizonProbe.set(0.0f, 0.0f, 1.0f, 1.0f).mul(invViewProj);
        horizonDir.set(horizonProbe.x, 0.0f, horizonProbe.z);

        if (horizonDir.lengthSquared() < 1e-6f) return;
        horizonDir.normalize();

        Vector3f linear = sky.skyColor(horizonDir);
        fogColorVec.set(
                exposed(linear.x) * skyBrightness,
                exposed(linear.y) * skyBrightness,
                exposed(linear.z) * skyBrightness);
    }

    private static float exposed(float linear) {
        return (float) (1.0 - Math.exp(-EXPOSURE * Math.max(linear, 0.0f)));
    }

    private static long countPass(List<DrawCall> calls, RenderPass pass) {
        return calls.stream().filter(c -> c.pass() == pass).count();
    }

    private void submit(DrawCall call, Camera camera) {
        if (call.atlas().isPresent()) call.atlas().get().bind();
        else glBindTexture(GL_TEXTURE_2D, 0);
        modelScratch.set(call.transform());
        call.shader().bind();
        call.shader().apply(frameUniforms, call.uniforms());
        call.mesh().render();
    }
}
