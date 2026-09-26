package com.beneklund.minecraft.util;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * An axis-aligned bounding box: two world-space corners with {@code min <= max} on every axis.
 *
 * <p>Chunk bounds for frustum culling and the player's body for collision both use it. Because
 * blocks are unit cubes on the integer grid, a box maps directly to the cells it occupies with
 * {@link #getBlocksOverlapping()}, which is how {@code Physics} finds the solids to resolve
 * against.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection">
 *     MDN: 3D collision detection</a>
 */
public class AABB {
    private final Vector3f min;
    private final Vector3f max;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        min = new Vector3f(minX, minY, minZ);
        max = new Vector3f(maxX, maxY, maxZ);
    }

    /**
     * A box whose bottom face is centred on {@code bottomCenter}. Entity positions sit at the feet,
     * so this is the body of an entity standing at that position.
     */
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

    /**
     * Every block cell this box shares volume with. Whether a cell is solid is the caller's
     * question.
     *
     * <p>Block {@code (x, y, z)} is the unit cube {@code [x, x+1]}. The range per axis is {@code
     * floor(min)} to {@code ceil(max) - 1}, a half-open upper edge: a face resting exactly on an
     * integer touches the next cell without entering it, so a body snapped flush against a wall
     * reports no collision with that wall on the other axes. {@code Math.floor} rounds negative
     * coordinates down, where an {@code int} cast would round them toward zero.
     */
    public List<Vector3i> getBlocksOverlapping() {
        int minBx = (int) Math.floor(min.x);
        int maxBx = (int) Math.ceil(max.x) - 1;
        int minBy = (int) Math.floor(min.y);
        int maxBy = (int) Math.ceil(max.y) - 1;
        int minBz = (int) Math.floor(min.z);
        int maxBz = (int) Math.ceil(max.z) - 1;

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

    /**
     * Whether the two boxes share volume.
     *
     * <p>Boxes overlap when their intervals overlap on all three axes; a gap on any one axis is a
     * separating plane, which is the separating axis theorem reduced to the three box axes. The
     * comparisons are strict, so boxes touching on a face don't intersect.
     */
    public boolean intersects(AABB other) {
        return min.x < other.max.x
                && max.x > other.min.x
                && min.y < other.max.y
                && max.y > other.min.y
                && min.z < other.max.z
                && max.z > other.min.z;
    }
}
