package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.Direction;
import com.beneklund.minecraft.world.IWorldView;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Walks a ray through the block grid, cell by cell, to the first solid block.
 *
 * <p>This is Amanatides and Woo's voxel traversal. Per axis it tracks {@code tMax}, the ray
 * parameter at which the ray next crosses a cell boundary on that axis, and {@code tDelta}, the
 * parameter needed to cross one whole cell. Each iteration steps into the neighbour across the
 * nearest boundary, so the walk visits exactly the cells the ray passes through, in order, with
 * one comparison and one add per step and no fixed step size to tune.
 *
 * @see <a href="http://www.cse.yorku.ca/~amana/research/grid.pdf">Amanatides and Woo, "A Fast Voxel
 *     Traversal Algorithm for Ray Tracing" (1987)</a>
 * @see <a href="https://web.archive.org/web/20121024081332/www.xnawiki.com/index.php?title=Voxel_traversal">
 *     XNA Wiki: Voxel traversal (archived)</a>
 */
public class Raycast {
    /**
     * Casts from {@code origin} along {@code direction} until a solid block or {@code
     * maxDistance}.
     *
     * <p>The origin's own cell is tested first; a hit there reports distance 0 and face {@link
     * Direction#NORTH}, since the ray entered through no face. Distances are the ray parameter
     * {@code t}, which is in blocks when {@code direction} has unit length. The hit face is the one
     * the ray entered through, opposite the step direction, which is the face a placed block
     * attaches to.
     */
    public static RaycastResult cast(Vector3f origin, Vector3f direction, IWorldView world, float maxDistance) {
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = (direction.x == 0.0f) ? 0 : (direction.x > 0 ? 1 : -1);
        int stepY = (direction.y == 0.0f) ? 0 : (direction.y > 0 ? 1 : -1);
        int stepZ = (direction.z == 0.0f) ? 0 : (direction.z > 0 ? 1 : -1);

        // The integer coordinate of the first voxel boundary the ray will cross on each axis.
        // If stepping positive, the next boundary is at x+1; if stepping negative (or zero), it's at x.
        Vector3i blockBoundary =
                new Vector3i(x + (stepX > 0 ? 1 : 0), y + (stepY > 0 ? 1 : 0), z + (stepZ > 0 ? 1 : 0));

        // tMax: the ray parameter t at which the ray first crosses a boundary on each axis.
        // Derived from solving origin + t*direction = boundary for t on each axis.
        Vector3f tMax = new Vector3f(
                (blockBoundary.x - origin.x) / direction.x,
                (blockBoundary.y - origin.y) / direction.y,
                (blockBoundary.z - origin.z) / direction.z);

        // NaN occurs when direction is 0 on that axis (0/0). Treat as never crossing that boundary.
        if (Float.isNaN(tMax.x)) tMax.x = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tMax.y)) tMax.y = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tMax.z)) tMax.z = Float.POSITIVE_INFINITY;

        // tDelta: how far along the ray (in t) we travel to cross one full voxel on each axis.
        // tDelta = step / direction, i.e. 1 / |direction| per axis.
        Vector3f tDelta = new Vector3f(stepX / direction.x, stepY / direction.y, stepZ / direction.z);
        if (Float.isNaN(tDelta.x)) tDelta.x = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tDelta.y)) tDelta.y = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tDelta.z)) tDelta.z = Float.POSITIVE_INFINITY;

        float distance = 0.0f;
        Direction hitFace = Direction.NORTH;

        while (true) {
            var block = world.getBlock(x, y, z);
            if (block != null && block.solid()) {
                return new RaycastResult(true, new Vector3i(x, y, z), block, hitFace, distance);
            }

            // Advance to the next voxel by crossing whichever axis boundary is nearest (smallest t).
            if (tMax.x < tMax.y && tMax.x < tMax.z) {
                distance = tMax.x;
                if (distance > maxDistance) break;
                x += stepX;
                // The face we entered from is opposite the step direction
                hitFace = stepX > 0 ? Direction.WEST : Direction.EAST;
                tMax.x += tDelta.x;
            } else if (tMax.y < tMax.z) {
                distance = tMax.y;
                if (distance > maxDistance) break;
                y += stepY;
                hitFace = stepY > 0 ? Direction.DOWN : Direction.UP;
                tMax.y += tDelta.y;
            } else {
                distance = tMax.z;
                if (distance > maxDistance) break;
                z += stepZ;
                hitFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
                tMax.z += tDelta.z;
            }
        }

        return new RaycastResult(false, new Vector3i(x, y, z), world.getBlock(x, y, z), hitFace, distance);
    }
}
