package com.beneklund.minecraft.util;

import com.beneklund.minecraft.world.IWorldAuthority;
import org.joml.Vector3f;
import org.joml.Vector3i;

// https://web.archive.org/web/20121024081332/www.xnawiki.com/index.php?title=Voxel_traversal

public class Raycast {
    public static RaycastResult cast(Vector3f origin, Vector3f direction, IWorldAuthority world, float maxDistance) {
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);
        int stepX = (direction.x == 0.0f) ? 0 : (direction.x > 0 ? 1 : -1);
        int stepY = (direction.y == 0.0f) ? 0 : (direction.y > 0 ? 1 : -1);
        int stepZ = (direction.z == 0.0f) ? 0 : (direction.z > 0 ? 1 : -1);

        Vector3i blockBoundary =
                new Vector3i(x + (stepX > 0 ? 1 : 0), y + (stepY > 0 ? 1 : 0), z + (stepZ > 0 ? 1 : 0));

        Vector3f tMax = new Vector3f(
                (blockBoundary.x - origin.x) / direction.x,
                (blockBoundary.y - origin.y) / direction.y,
                (blockBoundary.z - origin.z) / direction.z);

        if (Float.isNaN(tMax.x)) tMax.x = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tMax.y)) tMax.y = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tMax.z)) tMax.z = Float.POSITIVE_INFINITY;

        Vector3f tDelta = new Vector3f(stepX / direction.x, stepY / direction.y, stepZ / direction.z);
        if (Float.isNaN(tDelta.x)) tDelta.x = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tDelta.y)) tDelta.y = Float.POSITIVE_INFINITY;
        if (Float.isNaN(tDelta.z)) tDelta.z = Float.POSITIVE_INFINITY;

        float distance = 0.0f;
        Direction hitFace = Direction.NORTH;

        while (true) {
            var block = world.getBlock(x, y, z);
            if (block != null && block.solid()) {
                return new RaycastResult(true, new Vector3i(x, y, z), hitFace, distance);
            }

            if (tMax.x < tMax.y && tMax.x < tMax.z) {
                distance = tMax.x;
                if (distance > maxDistance) break;
                x += stepX;
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

        return new RaycastResult(false, new Vector3i(x, y, z), hitFace, distance);
    }
}
