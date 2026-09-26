package com.beneklund.minecraft.platform.graphics;

/** Coloured world-space line segments in {@link VertexFormat#LINE} layout, for debug overlays. */
public final class LineMesh extends Mesh {
    public LineMesh(Geometry geometry) {
        super(geometry, VertexFormat.LINE, PrimitiveMode.LINES);
    }
}
