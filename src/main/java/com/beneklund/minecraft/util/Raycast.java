package com.beneklund.minecraft.util;

import com.beneklund.minecraft.world.IWorldAuthority;
import org.joml.Vector3f;
import org.joml.Vector3i;

// https://web.archive.org/web/20121024081332/www.xnawiki.com/index.php?title=Voxel_traversal

public class Raycast {
    public static RaycastResult cast(Vector3f origin, Vector3f direction, IWorldAuthority world, float maxDistance) {
        // Start in the voxel that contains the ray origin
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        // +1, -1, or 0 — which direction we step along each axis
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
            // Check the current voxel before advancing
            var block = world.getBlock(x, y, z);
            if (block != null && block.solid()) {
                return new RaycastResult(true, new Vector3i(x, y, z), block, hitFace, distance);
            }

            // Advance to the next voxel by crossing whichever axis boundary is nearest (smallest t).
            if (tMax.x < tMax.y && tMax.x < tMax.z) {
                // X boundary is closest — step along X
                distance = tMax.x;
                if (distance > maxDistance) break;
                x += stepX;
                // The face we entered from is opposite the step direction
                hitFace = stepX > 0 ? Direction.WEST : Direction.EAST;
                tMax.x += tDelta.x; // advance to the next X boundary
            } else if (tMax.y < tMax.z) {
                // Y boundary is closest — step along Y
                distance = tMax.y;
                if (distance > maxDistance) break;
                y += stepY;
                hitFace = stepY > 0 ? Direction.DOWN : Direction.UP;
                tMax.y += tDelta.y;
            } else {
                // Z boundary is closest — step along Z
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
