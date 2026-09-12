package com.beneklund.minecraft.platform.graphics;

public final class SkyMesh extends Mesh {
    // spotless:off
    private static final float[] FULLSCREEN_TRIANGLE = {
        -1.0f, -1.0f,
         3.0f, -1.0f,
        -1.0f,  3.0f,
    };
    // spotless:on
    private static final int[] INDICES = {0, 1, 2};

    public SkyMesh() {
        super(new Geometry(FULLSCREEN_TRIANGLE, INDICES), VertexFormat.SKY, PrimitiveMode.TRIANGLES);
    }
}
