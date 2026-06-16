package com.beneklund.minecraft.util;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3i;

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

    // Every integer block cell this box actually penetrates. A block at (x,y,z) is the
    // unit cube [x,x+1]. The low bound is floor(min); the high bound is ceil(max)-1, which
    // is a half-open upper edge: a face resting exactly on an integer (max == 1.0) touches
    // the next cell but shares no volume with it, so we exclude it — otherwise a body
    // snapped flush against a wall would report a phantom collision on the other axes.
    // Math.floor (not an int cast) so negative coords map down, not toward zero.
    // Pure geometry — whether a cell is solid is the caller's problem.
    public List<Vector3i> getBlocksOverlapping() {
        int minBx = (int) Math.floor(this.min.x);
        int maxBx = (int) Math.ceil(this.max.x) - 1;
        int minBy = (int) Math.floor(this.min.y);
        int maxBy = (int) Math.ceil(this.max.y) - 1;
        int minBz = (int) Math.floor(this.min.z);
        int maxBz = (int) Math.ceil(this.max.z) - 1;

        List<Vector3i> cells = new ArrayList<>();
        for (int x = minBx; x <= maxBx; x++) {
            for (int y = minBy; y <= maxBy; y++) {
                for (int z = minBz; z <= maxBz; z++) {
                    cells.add(new Vector3i(x, y, z));
                }
            }
        }
        return cells;
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
