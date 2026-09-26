package com.beneklund.minecraft.renderer.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The sun's view-projection for each shadow cascade: the matrices the shadow map is rendered from
 * and sampled with.
 *
 * <p>Cascaded shadow maps split the view distance into bands, each covered by its own square
 * orthographic box around the eye. Near bands get small boxes and therefore fine texels; far bands
 * trade resolution for reach. The terrain shader picks a band per fragment by view distance, using
 * {@link #splitDistance}. The projection is orthographic because the sun's rays are parallel, and
 * orthographic depth is linear, so the generous {@code NEAR}/{@code FAR} range costs no precision.
 *
 * <p>{@link #update} takes an eye position and a sun direction, never a camera or view matrix. A
 * shadow map is a property of the world and the sun; the moment the view direction reaches it,
 * shadows change when the mouse moves. The class is pure maths with no GL, which lets {@code
 * ShadowCameraTest} pin properties that are invisible once the result is inside a depth texture.
 *
 * <p>Two quantisations keep the map stable frame to frame. The eye enters each cascade's matrix
 * only in whole texels of that cascade, and the sun direction moves in quarter-degree steps; see
 * {@link #update} and {@link #quantiseSunDirection()}.
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/dxtecharts/cascaded-shadow-maps">
 *     Microsoft: Cascaded Shadow Maps</a>
 * @see <a
 *     href="https://learn.microsoft.com/en-us/windows/win32/dxtecharts/common-techniques-to-improve-shadow-depth-maps">
 *     Microsoft: Common Techniques to Improve Shadow Depth Maps (texel snapping, bias)</a>
 * @see <a href="https://learnopengl.com/Guest-Articles/2021/CSM">LearnOpenGL: Cascaded Shadow
 *     Mapping</a>
 * @see <a
 *     href="https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-10-parallel-split-shadow-maps-programmable-gpus">
 *     GPU Gems 3, ch. 10: Parallel-Split Shadow Maps</a>
 */
public class ShadowCamera {

    /**
     * Half-width of each cascade's square, in blocks. The array length is the cascade count; add a
     * cascade by adding an entry here and to {@link #SPLIT_DISTANCES}.
     *
     * <p>At a 2048 map these are 0.031, 0.125 and 0.5 blocks per texel. The near cascade, where
     * shadow edges close to the player are rasterised, is four times finer than a single 128-block
     * map.
     */
    private static final float[] BOX_HALVES = {32.0f, 128.0f, 512.0f};

    /**
     * View distance at which each cascade hands over to the next, in blocks. The last entry ends
     * shadowing.
     *
     * <p>The first split is 28, inside the 32-block box, because the box is axis-aligned in light
     * space: a fragment 32 blocks along the view ray can sit outside a box of half-width 32. The
     * margin keeps every handover inside the box it leaves.
     */
    private static final float[] SPLIT_DISTANCES = {28.0f, 120.0f, 500.0f};

    // Widest cascade, for anything that needs one number for the whole system.
    public static final float BOX_HALF = 128.0f;

    /**
     * How far from the eye a chunk can be and still cast into {@code cascade}, with a flat 128-block
     * margin that covers a tall caster outside the box throwing a shadow across it at low sun.
     * {@code ChunkRenderer} uses the sun-aware overload.
     */
    public static float casterRadius(int cascade) {
        return BOX_HALVES[cascade] + 128.0f;
    }

    // World height. The tallest a caster can stand above what it shadows, which is what turns a
    // sun elevation into a horizontal shadow length.
    private static final float MAX_CASTER_HEIGHT = 256.0f;

    // The flat margin above, kept as the ceiling: it is roughly the answer for a sun low enough
    // that the exact formula stops being useful.
    private static final float MAX_SHADOW_REACH = 128.0f;

    /**
     * How far a shadow reaches horizontally at this sun elevation, in blocks.
     *
     * <p>A caster {@code h} blocks above the surface lands its shadow {@code h / tan(elevation)}
     * away. For a unit sun direction {@code tan(elevation) = y / |xz|}, so the reach is {@code h *
     * |xz| / y}. At noon {@code |xz|} is near 0 and shadows fall straight down, so a caster outside
     * the box can't reach into it.
     *
     * <p>Clamped to 128, because the formula runs to infinity as the sun reaches the horizon. With
     * the sun below the horizon there is nothing to cast, and the clamp is the conservative answer.
     */
    public static float shadowReach(Vector3fc sunDirection) {
        float up = sunDirection.y();
        if (up <= 0.0f) return MAX_SHADOW_REACH;
        float horizontal = (float) Math.sqrt(sunDirection.x() * sunDirection.x() + sunDirection.z() * sunDirection.z());
        return Math.min(MAX_CASTER_HEIGHT * horizontal / up, MAX_SHADOW_REACH);
    }

    /**
     * Caster radius for one cascade, with the sun taken into account.
     *
     * @param upSun how far the chunk sits on the side the light comes from, 0 to 1. A caster
     *     down-sun of the box throws its shadow away from it, so at 0 it gets no margin and is
     *     tested against the bare box.
     */
    public static float casterRadius(int cascade, Vector3fc sunDirection, float upSun) {
        return BOX_HALVES[cascade] + shadowReach(sunDirection) * upSun;
    }

    public static int cascadeCount() {
        return BOX_HALVES.length;
    }

    public static float splitDistance(int cascade) {
        return SPLIT_DISTANCES[cascade];
    }

    // How far back along the sun ray the light's eye sits. Only has to clear the tallest terrain
    // that could cast into the box; MAX_SURFACE_Y is 250, so this is generous.
    private static final float SUN_DISTANCE = 600.0f;

    private static final float NEAR = 1.0f;
    private static final float FAR = 1200.0f;

    /**
     * Depth margin between a fragment and the nearest surface the sun saw, in texels.
     *
     * <p>Shadow acne is a texel-footprint problem: one stored depth stands for a whole texel of
     * world, and a surface tilted to the sun varies in depth across it. The margin scales with how
     * much world a texel covers, so stating it in texels gives each cascade a proportional bias
     * from one number.
     *
     * <p>2.4 texels is the value tuned on the original single 128-block map: {@code 0.3 blocks /
     * (256 blocks / 2048 texels) = 2.4 texels}.
     */
    private static final float BIAS_TEXELS = 2.4f;

    // Half-depth of the band the debug overlay stretches to full contrast, in blocks either side
    // of the box centre. Chunk.SIZE_Y is 256, so this comfortably contains any caster.
    private static final float DEPTH_WINDOW = 300.0f;

    /**
     * Lowest sun elevation the shadow box sees, as {@code sin(elevation)}: about 20 degrees.
     *
     * <p>The box is a fixed square in light space. As the sun nears the horizon that square stands
     * on its edge, spans 256 blocks vertically, spends most of its texels on sky and catches
     * terrain in a thin strip. Ground resolution collapses and geometry crosses the strip boundary
     * constantly, which reads as flicker. The clamp keeps the box looking down; late-afternoon
     * shadows point at a slightly wrong angle, which is invisible by comparison. Fading shadows
     * out near the horizon is the usual engine answer and isn't implemented.
     */
    private static final float MIN_SUN_ELEVATION = 0.35f;

    // Angular step the sun is rounded to before it is allowed to move the shadow map. See
    // quantiseSunDirection. A quarter of a degree is a step every ~0.8 s at DEFAULT_DAY_SECONDS.
    private static final float SUN_STEP_RADIANS = (float) Math.toRadians(0.25);

    private static final Vector3f Y_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f Z_UP = new Vector3f(0.0f, 0.0f, 1.0f);
    private static final Vector3f ORIGIN = new Vector3f();

    private final int mapSize;

    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightProj = new Matrix4f();
    private final Matrix4f[] lightViewProj = new Matrix4f[cascadeCount()];
    private final Matrix4f lightViewInv = new Matrix4f();
    private final Vector3f sceneCenter = new Vector3f();
    private final Vector3f lightEye = new Vector3f();
    private final Vector3f lightUp = new Vector3f();
    private final Vector3f shadowSunDir = new Vector3f();
    private final Vector3f snapScratch = new Vector3f();

    /** @param mapSize shadow map width and height in texels */
    public ShadowCamera(int mapSize) {
        this.mapSize = mapSize;
        for (int i = 0; i < lightViewProj.length; i++) lightViewProj[i] = new Matrix4f();
    }

    /**
     * Rebuilds every cascade's matrix for this eye position and sun. Runs every frame.
     *
     * <p>Each box is centred on the eye so the shadowed region follows the player; a world-anchored
     * box would run out of shadows as soon as the player walked out of it. The eye enters only in
     * whole texels of each cascade's own grid, which keeps the texel grid anchored to the world.
     */
    public void update(Vector3fc eyePosition, Vector3fc sunDirection) {
        // Shadows use their own copy of the sun, floored in elevation. See the constant.
        shadowSunDir.set(sunDirection);
        if (shadowSunDir.y < MIN_SUN_ELEVATION) {
            shadowSunDir.y = MIN_SUN_ELEVATION;
            shadowSunDir.normalize();
        }
        quantiseSunDirection();

        // lookAt builds its basis from a cross product with `up`, which is the zero vector when
        // `up` is parallel to the view direction — and that produces NaN, a black screen, and no
        // error. The sun points straight down at noon, so this is not a corner case.
        lightUp.set(Math.abs(shadowSunDir.y) > 0.99f ? Z_UP : Y_UP);

        /*
         * Texel snapping, and it has to happen before the projection exists.
         *
         * The box is centred on the eye, so without this it slides by fractions of a texel as the
         * player walks: the map's grid is anchored to the player rather than to the world, and
         * every sub-texel slide re-rasterises each shadow edge into a different set of texels.
         * That is the shimmer.
         *
         * Snapping the centre *after* projecting it is useless — the centre is the lookAt target,
         * so it always lands at NDC (0,0) and rounding that changes nothing. What has to land on a
         * grid is the centre's position in world space, measured along the light's own axes.
         *
         * So: build the light's rotation first (it depends only on the sun), express the eye in
         * that space, round to whole texels there, and convert back. Both lookAt calls share the
         * same direction and up, so they share a rotation R, and the second view is just
         * view1 - R*centre — snapping R*centre to whole texels therefore leaves the texel grid
         * anchored to the world rather than to the player.
         *
         * All three axes are snapped, not just x and y. Depth along the light axis shifts every
         * stored depth and every fragment depth by the same amount, so leaving z loose was
         * harmless to the comparison — but it meant a sub-texel step still produced a different
         * matrix, and "the eye only enters in whole texels" is a far easier property to rely on,
         * and to test, when it has no exceptions.
         */
        // Each cascade snaps to its OWN texel grid, because each covers a different amount of
        // world through the same number of texels. Sharing one grid would leave the near cascade
        // snapping in steps four times larger than its own texels — which is no snapping at all.
        for (int cascade = 0; cascade < cascadeCount(); cascade++) {
            updateCascade(eyePosition, cascade);
        }
    }

    private void updateCascade(Vector3fc eyePosition, int cascade) {
        float boxHalf = BOX_HALVES[cascade];

        lightEye.set(shadowSunDir).mul(SUN_DISTANCE);
        lightView.identity().lookAt(lightEye, ORIGIN, lightUp);

        float texel = texelWorldSize(cascade);
        snapScratch.set(eyePosition);
        lightView.transformPosition(snapScratch);
        snapScratch.x = Math.round(snapScratch.x / texel) * texel;
        snapScratch.y = Math.round(snapScratch.y / texel) * texel;
        snapScratch.z = Math.round(snapScratch.z / texel) * texel;
        lightViewInv.set(lightView).invert().transformPosition(snapScratch);
        sceneCenter.set(snapScratch);

        // Rebuild for real, now centred on a point that only moves a whole texel at a time.
        lightEye.set(shadowSunDir).mul(SUN_DISTANCE).add(sceneCenter);
        lightView.identity().lookAt(lightEye, sceneCenter, lightUp);
        lightProj.identity().ortho(-boxHalf, boxHalf, -boxHalf, boxHalf, NEAR, FAR);
        lightProj.mul(lightView, lightViewProj[cascade]);
    }

    /**
     * Rounds the shadow sun's azimuth and elevation to {@code SUN_STEP_RADIANS}, so the light's
     * rotation holds still between steps.
     *
     * <p>Texel snapping fixes where the box sits and leaves its orientation free, and the rotation
     * is rebuilt from the sun every frame. A continuously turning rotation caused two visible
     * faults:
     *
     * <ol>
     *   <li>Every world point's light-space position drifts, so the whole map re-rasterises each
     *       frame and every shadow edge crawls.
     *   <li>The snap is computed from the eye in light space, and rotating by {@code dTheta} moves
     *       that value by {@code |eye| * dTheta}. With the player 700 blocks from the origin and a
     *       20-minute day that is half a texel per frame, so {@code Math.round} stepped almost
     *       every frame and jerked the whole map back a texel while it drifted forward. Measured
     *       before this fix: a fixed world point reversed direction in the map 231 times in 240
     *       frames.
     * </ol>
     *
     * <p>With the sun held between steps, the matrix is bit-identical frame to frame. Shadows move
     * in steps; at a quarter degree an edge moves under a tenth of a block per step, which reads as
     * motion. A smaller step drifts back toward per-frame re-rasterisation; a larger one gives
     * steady shadows that visibly click round.
     */
    private void quantiseSunDirection() {
        float azimuth = (float) Math.atan2(shadowSunDir.x, shadowSunDir.z);
        float elevation = (float) Math.asin(Math.max(-1.0f, Math.min(1.0f, shadowSunDir.y)));

        azimuth = Math.round(azimuth / SUN_STEP_RADIANS) * SUN_STEP_RADIANS;
        elevation = Math.round(elevation / SUN_STEP_RADIANS) * SUN_STEP_RADIANS;

        float horizontal = (float) Math.cos(elevation);
        shadowSunDir
                .set(
                        (float) Math.sin(azimuth) * horizontal,
                        (float) Math.sin(elevation),
                        (float) Math.cos(azimuth) * horizontal)
                .normalize();
    }

    /** Live view of the cascade's matrix, reused each frame and handed straight to a uniform. */
    public Matrix4f lightViewProj(int cascade) {
        return lightViewProj[cascade];
    }

    /**
     * The cascade's bias in the map's [0, 1] depth units, which is what the shader compares in.
     * Per cascade because the bias is authored in texels and a texel covers a different amount of
     * world in each.
     */
    public float normalizedBias(int cascade) {
        return (BIAS_TEXELS * texelWorldSize(cascade)) / (FAR - NEAR);
    }

    /**
     * Lower end of the slice of [0, 1] shadow depth that terrain occupies, for the debug overlay
     * to stretch to full contrast. The light's eye sits {@code SUN_DISTANCE} in front of the box
     * centre, so the centre lands at that depth and terrain reaches about a world height either
     * side.
     */
    public float depthWindowMin() {
        return (SUN_DISTANCE - DEPTH_WINDOW - NEAR) / (FAR - NEAR);
    }

    /** Upper end of the window; see {@link #depthWindowMin()}. */
    public float depthWindowMax() {
        return (SUN_DISTANCE + DEPTH_WINDOW - NEAR) / (FAR - NEAR);
    }

    /** One shadow texel in blocks for {@code cascade}: the unit the snapping and bias are stated in. */
    public float texelWorldSize(int cascade) {
        return (2.0f * BOX_HALVES[cascade]) / mapSize;
    }

    /**
     * The sun direction the shadow map was built from, after the elevation floor and quantisation.
     * Differs from the sky's and the lighting's sun whenever the real sun is low.
     *
     * <p>Raising {@code y} to {@code MIN_SUN_ELEVATION} and renormalising pulls {@code y} back down
     * slightly, so a near-horizontal sun settles a little under 20 degrees. The goal is a box that
     * looks down, so the exact angle doesn't matter.
     */
    public Vector3fc effectiveSunDirection() {
        return shadowSunDir;
    }
}
