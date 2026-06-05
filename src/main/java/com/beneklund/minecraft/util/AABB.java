package com.beneklund.minecraft.util;

import org.joml.Vector3f;

// Axis-aligned bounding box: two world-space corners with min <= max on every axis.
// Used for frustum culling (chunk bounds) now, and entity/block collision later.
public class AABB {
    private final Vector3f min;
    private final Vector3f max;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.min = new Vector3f(minX, minY, minZ);
        this.max = new Vector3f(maxX, maxY, maxZ);
    }

    // Box of the given footprint/height whose bottom face is centered on bottomCenter.
    // Matches how an entity stands: position sits at the feet, the body extends upward.
    public static AABB ofSize(Vector3f bottomCenter, float width, float height, float depth) {
        float halfWidth = width / 2f;
        float halfDepth = depth / 2f;
        return new AABB(
                bottomCenter.x - halfWidth,
                bottomCenter.y,
                bottomCenter.z - halfDepth,
                bottomCenter.x + halfWidth,
                bottomCenter.y + height,
                bottomCenter.z + halfDepth);
    }

    public float minX() {
        return min.x;
    }

    public float minY() {
        return min.y;
    }

    public float minZ() {
        return min.z;
    }

    public float maxX() {
        return max.x;
    }

    public float maxY() {
        return max.y;
    }

    public float maxZ() {
        return max.z;
    }

    // True if the two boxes share volume. They overlap only if their extents overlap on
    // all three axes at once — a gap on any single axis means a flat plane fits between
    // them (separating-axis theorem, trivial for axis-aligned boxes). On one axis, the
    // intervals [min,max] overlap when each box's min sits below the other's max. Strict
    // <, so boxes that only touch on a face don't count as intersecting.
    public boolean intersects(AABB other) {
        return min.x < other.max.x
                && max.x > other.min.x
                && min.y < other.max.y
                && max.y > other.min.y
                && min.z < other.max.z
                && max.z > other.min.z;
    }
}
