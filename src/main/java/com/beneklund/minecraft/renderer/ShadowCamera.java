package com.beneklund.minecraft.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/*
 * The sun's view-projection: where the shadow map is rendered from.
 *
 * Pure maths on purpose — no GL, no Camera, no window. That is what lets ShadowCameraTest pin the
 * properties below, none of which can be checked once the answer only exists inside a depth
 * texture.
 *
 * Note what update() accepts: an eye POSITION and a sun direction. Not a Camera. Where the player
 * is looking is deliberately not expressible here, because it must not matter — a shadow map is a
 * property of the world and the sun, and the moment the viewer leaks into it, shadows change when
 * you move the mouse. Keep it that way; pass a position, never a camera or a view matrix.
 *
 * Orthographic, not perspective: the sun's rays are parallel, so there is no vanishing point.
 * Ortho depth is also linear, which is why NEAR/FAR can be generous without costing the precision
 * a wide perspective range would.
 */
public class ShadowCamera {

    // Half-width of the square area the sun's shadow map covers, in blocks. 128 puts a 256-block
    // box around the player, which at a 2048 map is one texel per eighth of a block — crisp.
    // Raising it trades sharpness for coverage, and that trade is what cascades exist to stop
    // having to make.
    public static final float BOX_HALF = 128.0f;

    // How far from the camera a chunk can be and still cast into the box. The box itself is
    // BOX_HALF wide; the extra margin is for low sun, where a tall caster outside the box still
    // throws a shadow across it. Read by ChunkRenderer, which does the actual selection.
    public static final float CASTER_RADIUS = BOX_HALF + 128.0f;

    // How far back along the sun ray the light's eye sits. Only has to clear the tallest terrain
    // that could cast into the box; MAX_SURFACE_Y is 250, so this is generous.
    private static final float SUN_DISTANCE = 600.0f;

    private static final float NEAR = 1.0f;
    private static final float FAR = 1200.0f;

    // Depth margin between a fragment and the nearest surface the sun saw, in BLOCKS. Too small
    // and lit surfaces speckle with acne; too large and a caster floats above its own shadow. The
    // shader wants it in the map's [0,1] depth units, so normalizedBias() divides it by the slab
    // depth — authoring it normalized is how you end up with a 1.8-block bias without noticing.
    private static final float BIAS_BLOCKS = 0.3f;

    // Half-depth of the band the debug overlay stretches to full contrast, in blocks either side
    // of the box centre. Chunk.SIZE_Y is 256, so this comfortably contains any caster.
    private static final float DEPTH_WINDOW = 300.0f;

    /*
     * Lowest sun elevation the shadow box is allowed to see, as sin(elevation) — about 20 degrees.
     *
     * The box is a fixed square in light space. As the sun approaches the horizon the light looks
     * nearly sideways, so that square stands on its edge: it spans 256 blocks *vertically*, spends
     * most of its texels on empty sky, and catches terrain only in a thin strip. Ground resolution
     * collapses and geometry crosses the strip boundary constantly, which reads as flicker.
     *
     * Clamping keeps the box looking down enough to stay full of terrain. It makes late-afternoon
     * shadows point at a slightly wrong angle, which is invisible next to the alternative. Real
     * engines fade shadows out near the horizon instead; that is a later card.
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
    private final Matrix4f lightViewProj = new Matrix4f();
    private final Matrix4f lightViewInv = new Matrix4f();
    private final Vector3f sceneCenter = new Vector3f();
    private final Vector3f lightEye = new Vector3f();
    private final Vector3f lightUp = new Vector3f();
    private final Vector3f shadowSunDir = new Vector3f();
    private final Vector3f snapScratch = new Vector3f();

    public ShadowCamera(int mapSize) {
        this.mapSize = mapSize;
    }

    /*
     * Rebuilt every frame because it depends on where the player is standing as well as where the
     * sun is. Anchoring it to the eye is what keeps the shadowed region under the player instead
     * of at a fixed point in the world — which is also why the eye cannot simply be dropped as an
     * input: a world-anchored box would run out of shadows as soon as you walked out of it.
     *
     * What it can do is enter only in whole texels. See the snapping below.
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
        lightEye.set(shadowSunDir).mul(SUN_DISTANCE);
        lightView.identity().lookAt(lightEye, ORIGIN, lightUp);

        float texelWorldSize = (2.0f * BOX_HALF) / mapSize;
        snapScratch.set(eyePosition);
        lightView.transformPosition(snapScratch);
        snapScratch.x = Math.round(snapScratch.x / texelWorldSize) * texelWorldSize;
        snapScratch.y = Math.round(snapScratch.y / texelWorldSize) * texelWorldSize;
        snapScratch.z = Math.round(snapScratch.z / texelWorldSize) * texelWorldSize;
        lightViewInv.set(lightView).invert().transformPosition(snapScratch);
        sceneCenter.set(snapScratch);

        // Rebuild for real, now centred on a point that only moves a whole texel at a time.
        lightEye.set(shadowSunDir).mul(SUN_DISTANCE).add(sceneCenter);
        lightView.identity().lookAt(lightEye, sceneCenter, lightUp);
        lightProj.identity().ortho(-BOX_HALF, BOX_HALF, -BOX_HALF, BOX_HALF, NEAR, FAR);
        lightProj.mul(lightView, lightViewProj);
    }

    /*
     * The sun enters in discrete steps, for the same reason the eye does.
     *
     * Texel snapping quantises where the box sits. It cannot quantise how the box is ORIENTED, and
     * the light's rotation is rebuilt from the sun every frame. Two things follow from a rotation
     * that changes continuously, and both were visible:
     *
     * 1. Every world point's light-space position drifts, so the whole map re-rasterises each
     *    frame and every shadow edge crawls.
     * 2. Worse, the snap is computed from the eye expressed in light space. Rotating by dTheta
     *    moves that value by |eye| * dTheta — with the player 700 blocks from the world origin and
     *    a 20-minute day, half a texel per frame. Math.round then steps almost every frame, and
     *    each step jerks the ENTIRE map back one texel while it drifts forward. Measured before
     *    this: a fixed world point reversed direction in the map 231 times in 240 frames. That is
     *    the "back and forth, up and down" — it is judder, not the sun crossing the sky.
     *
     * Holding the sun still between steps makes the whole matrix bit-identical frame to frame, so
     * neither happens. The cost is that shadows update in steps rather than continuously; at this
     * step size a shadow edge moves under a tenth of a block per step, which reads as motion.
     *
     * The step is a quality knob. Smaller means smoother steps but a return toward per-frame
     * re-rasterisation; larger means rock-steady shadows that visibly click round.
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

    // Live view of the matrix, reused each frame — the renderer hands it straight to a uniform.
    public Matrix4f lightViewProj() {
        return lightViewProj;
    }

    // The bias in the map's [0,1] depth units, which is what the shader compares in.
    public float normalizedBias() {
        return BIAS_BLOCKS / (FAR - NEAR);
    }

    /*
     * The slice of the shadow map's [0,1] depth range that terrain actually occupies, for the
     * debug overlay to stretch across its contrast range.
     *
     * The light's eye sits SUN_DISTANCE in front of the box centre, so the centre lands at that
     * depth; terrain reaches roughly a world-height either side of it. Everything outside this
     * window is either empty sky or below bedrock.
     */
    public float depthWindowMin() {
        return (SUN_DISTANCE - DEPTH_WINDOW - NEAR) / (FAR - NEAR);
    }

    public float depthWindowMax() {
        return (SUN_DISTANCE + DEPTH_WINDOW - NEAR) / (FAR - NEAR);
    }

    // One shadow texel in blocks. Exposed because the snapping guarantee is stated in these units.
    public float texelWorldSize() {
        return (2.0f * BOX_HALF) / mapSize;
    }

    /*
     * The sun direction the shadow map was actually built from, after the elevation floor. Differs
     * from the sun the sky and the lighting use whenever the real sun is low.
     *
     * Worth knowing that the floor is approximate: raising y to MIN_SUN_ELEVATION and then
     * renormalising pulls y back down a little, so a near-horizontal sun settles slightly under
     * the nominal 20 degrees rather than exactly on it. Harmless — the point is to get the box
     * looking downward, not to hit a specific angle.
     */
    public Vector3fc effectiveSunDirection() {
        return shadowSunDir;
    }
}
