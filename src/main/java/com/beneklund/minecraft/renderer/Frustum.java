package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.util.AABB;
import org.joml.Matrix4f;

// The six planes bounding what the camera can see, pulled straight out of the
// view-projection matrix (Gribb/Hartmann method).
//
// Why this works: multiplying a world point P by VP gives its clip-space coords
// (x', y', z', w'), where each component is one row of VP dotted with P:
//     x' = row0·P    y' = row1·P    z' = row2·P    w' = row3·P
// A point is inside the view volume when it passes the clip test on every axis:
//     -w' <= x' <= w'    -w' <= y' <= w'    -w' <= z' <= w'
// Rearrange any one of those bounds into ">= 0" form and the coefficients ARE a plane:
//     left  edge:  x' >= -w'  ->  x' + w' >= 0  ->  (row0 + row3)·P >= 0
//     right edge:  x' <=  w'  ->  w' - x' >= 0  ->  (row3 - row0)·P >= 0
// Bottom/top fall out of row1 the same way, near/far out of row2. Each resulting
// (a,b,c,d) is a plane whose normal points inward, so plugging in a point and getting
// a positive number means "on the inside of that plane".
//
// Normals are left un-normalized on purpose — we only ever read the sign of that
// number, never the true distance, so dividing through by |normal| would be wasted work.
//
// JOML is column-major with accessors m{col}{row}, so row r of the matrix is
// (m0r, m1r, m2r, m3r) — e.g. row3 is (m03, m13, m23, m33).
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

    public boolean isVisible(AABB box) {
        return isVisible(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    // True unless the box is fully outside the frustum: if it sits entirely behind
    // any single plane, the camera can't see it.
    //
    // Rather than test all 8 corners against a plane, we test only the one corner that
    // reaches furthest toward that plane's inside — the "positive vertex." Build it per
    // axis: take max on an axis where the normal points +, min where it points -. If even
    // that best-case corner has a negative signed distance (is behind the plane), then all
    // 8 corners are too, so the whole box is outside and we cull it. One dot product per
    // plane instead of eight.
    private boolean isVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float px = nx[i] >= 0 ? maxX : minX;
            float py = ny[i] >= 0 ? maxY : minY;
            float pz = nz[i] >= 0 ? maxZ : minZ;
            // signed distance of the positive vertex from plane i; < 0 means fully outside
            if (nx[i] * px + ny[i] * py + nz[i] * pz + d[i] < 0) return false;
        }
        return true;
    }
}
