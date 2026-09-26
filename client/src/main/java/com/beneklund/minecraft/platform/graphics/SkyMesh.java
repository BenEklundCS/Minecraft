package com.beneklund.minecraft.platform.graphics;

/**
 * One triangle that covers the whole viewport, in {@link VertexFormat#SKY} layout. The sky, the
 * clouds and every post-processing pass draw with it.
 *
 * <p>The vertices sit at clip-space (-1,-1), (3,-1) and (-1,3). The rasteriser clips everything
 * outside [-1, 1], leaving exactly the screen. GPUs shade in 2x2 pixel quads, and a two-triangle
 * quad shades the pixel quads along its diagonal twice; one triangle has no diagonal.
 *
 * @see <a href="https://wallisc.github.io/rendering/2021/04/18/Fullscreen-Pass.html">Chris Wallis:
 *     Optimizing Triangles for a Full-screen Pass</a>
 */
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
