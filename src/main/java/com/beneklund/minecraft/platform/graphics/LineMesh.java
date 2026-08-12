package com.beneklund.minecraft.platform.graphics;

public final class LineMesh extends Mesh {
    public LineMesh(Geometry geometry) {
        super(geometry, VertexFormat.LINE, PrimitiveMode.LINES);
    }
}
