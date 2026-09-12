package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.platform.graphics.SkyMesh;
import java.util.List;
import org.joml.Matrix4f;

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
