package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.*;

/** Screen-space HUD triangles in {@link VertexFormat#HUD} layout. */
public final class HudMesh extends Mesh {
    public HudMesh(Geometry geometry) {
        super(geometry, VertexFormat.HUD, PrimitiveMode.TRIANGLES);
    }
}
