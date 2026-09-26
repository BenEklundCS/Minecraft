package com.beneklund.minecraft.renderer.sky;

import com.beneklund.minecraft.platform.graphics.SkyMesh;
import com.beneklund.minecraft.renderer.DrawCall;
import com.beneklund.minecraft.renderer.IRenderable;
import com.beneklund.minecraft.renderer.asset.ShaderProgram;
import com.beneklund.minecraft.renderer.camera.Camera;
import java.util.List;
import org.joml.Matrix4f;

/**
 * Draws the sky as one fullscreen triangle in the opaque pass.
 *
 * <p>{@code sky.frag} rebuilds each pixel's world-space view direction from the frame uniforms and
 * evaluates the Preetham daylight model there, then composites the cloud buffer on top. All of its
 * inputs are frame uniforms {@link Renderer} uploads, so the draw call carries only the mesh and
 * program.
 */
public class SkyRenderer implements IRenderable {
    private static final String VERT_PATH = "/shaders/sky.vert";
    private static final String FRAG_PATH = "/shaders/sky.frag";
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final ShaderProgram skyShader;
    private final SkyMesh skyMesh;

    public SkyRenderer() {
        skyShader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        skyMesh = new SkyMesh();
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        Matrix4f invViewProj = new Matrix4f(camera.getViewProjectionMatrix()).invert();
        return List.of(new DrawCall(skyMesh, IDENTITY, skyShader));
    }

    @Override
    public void reload() {
        skyShader.reload();
    }

    @Override
    public void delete() {
        skyShader.delete();
        skyMesh.delete();
    }
}
