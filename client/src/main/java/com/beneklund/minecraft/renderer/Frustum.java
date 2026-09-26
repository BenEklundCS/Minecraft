package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.util.AABB;
import org.joml.Matrix4f;

/**
 * The six planes bounding the camera's view volume, extracted from the view-projection matrix by
 * the Gribb and Hartmann method, and a conservative box test against them.
 *
 * <p>A world point {@code P} maps to clip space as {@code (x', y', z', w')}, each component one row
 * of the matrix dotted with {@code P}. {@code P} is inside when {@code -w' <= x', y', z' <= w'}.
 * Each of those six inequalities rearranged into {@code (...) . P >= 0} is a plane: left is {@code
 * (row3 + row0) . P >= 0}, right is {@code (row3 - row0) . P >= 0}, and bottom, top, near and far
 * follow from rows 1 and 2. The plane normals point inward, so a positive result means the inside
 * of that plane.
 *
 * <p>The normals stay unnormalised because the test reads only the sign of the result, never the
 * distance. JOML accessors are {@code m<col><row>}, so row {@code r} is {@code (m0r, m1r, m2r,
 * m3r)}.
 *
 * @see <a
 *     href="https://www.gamedevs.org/uploads/fast-extraction-viewing-frustum-planes-from-world-view-projection-matrix.pdf">
 *     Gribb and Hartmann: Fast Extraction of Viewing Frustum Planes from the World-View-Projection
 *     Matrix</a>
 */
public class Frustum {
    private final float[] nx = new float[6];
    private final float[] ny = new float[6];
    private final float[] nz = new float[6];
    private final float[] d = new float[6];

    public Frustum(Matrix4f vp) {
        // Left:   row3 + row0
        set(0, vp.m03() + vp.m00(), vp.m13() + vp.m10(), vp.m23() + vp.m20(), vp.m33() + vp.m30());
        // Right:  row3 - row0
        set(1, vp.m03() - vp.m00(), vp.m13() - vp.m10(), vp.m23() - vp.m20(), vp.m33() - vp.m30());
        // Bottom: row3 + row1
        set(2, vp.m03() + vp.m01(), vp.m13() + vp.m11(), vp.m23() + vp.m21(), vp.m33() + vp.m31());
        // Top:    row3 - row1
        set(3, vp.m03() - vp.m01(), vp.m13() - vp.m11(), vp.m23() - vp.m21(), vp.m33() - vp.m31());
        // Near:   row3 + row2
        set(4, vp.m03() + vp.m02(), vp.m13() + vp.m12(), vp.m23() + vp.m22(), vp.m33() + vp.m32());
        // Far:    row3 - row2
        set(5, vp.m03() - vp.m02(), vp.m13() - vp.m12(), vp.m23() - vp.m22(), vp.m33() - vp.m32());
    }

    private void set(int i, float x, float y, float z, float w) {
        nx[i] = x;
        ny[i] = y;
        nz[i] = z;
        d[i] = w;
    }

    /**
     * False only when the box lies entirely behind one plane. Boxes near a frustum corner can pass
     * while outside it; that costs a draw and never hides visible geometry.
     */
    public boolean isVisible(AABB box) {
        return isVisible(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /**
     * Tests each plane against the box's positive vertex: per axis, the max where the normal
     * component is positive and the min where it is negative. That corner reaches furthest into
     * the plane's inside, so if it is behind the plane, all eight corners are. One dot product per
     * plane.
     */
    private boolean isVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float px = nx[i] >= 0 ? maxX : minX;
            float py = ny[i] >= 0 ? maxY : minY;
            float pz = nz[i] >= 0 ? maxZ : minZ;
            if (nx[i] * px + ny[i] * py + nz[i] * pz + d[i] < 0) return false;
        }
        return true;
    }
}
