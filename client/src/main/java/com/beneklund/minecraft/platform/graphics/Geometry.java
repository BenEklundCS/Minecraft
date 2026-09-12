package com.beneklund.minecraft.platform.graphics;

import java.util.Arrays;

public record Geometry(float[] vertices, int[] indices) {
    private static final int MAGIC = 31;
    public static final Geometry EMPTY = new Geometry(new float[] {}, new int[] {});

    public boolean isEmpty() {
        return indices.length == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Geometry(float[] v, int[] i))) return false;
        return Arrays.equals(i, indices()) && Arrays.equals(v, vertices());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vertices) * MAGIC + Arrays.hashCode(indices);
    }
}
