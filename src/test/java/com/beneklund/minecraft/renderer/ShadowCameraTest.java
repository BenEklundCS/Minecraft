package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/*
 * Pins the properties that keep shadows still while the player moves.
 *
 * The one that started this: shadows changed as the mouse moved. Orientation is now impossible to
 * pass in — ShadowCamera.update takes an eye position and a sun direction, never a Camera or a
 * view matrix — so that guarantee is structural rather than something a test has to re-check.
 * What still needs guarding is the eye POSITION, which genuinely is an input and must only ever
 * enter a whole texel at a time.
 */
class ShadowCameraTest {

    private static final int MAP_SIZE = 2048;
    // A late-morning sun: high enough not to trip the elevation clamp.
    private static final Vector3f SUN = new Vector3f(0.3f, 0.8f, 0.5f).normalize();

    private final ShadowCamera camera = new ShadowCamera(MAP_SIZE);

    private Matrix4f matrixAt(float x, float y, float z) {
        camera.update(new Vector3f(x, y, z), SUN);
        return new Matrix4f(camera.lightViewProj());
    }

    /*
     * The whole point of texel snapping: the eye may only enter a whole texel at a time. Walking a
     * fraction of a texel must leave the shadow map's grid exactly where it was, or every shadow
     * edge re-rasterises into a different set of texels each frame and the result crawls.
     *
     * Stated as a count of distinct matrices over a continuous walk rather than as "these two
     * positions agree", because where the texel boundaries happen to fall is arbitrary — a
     * fixed pair of samples can straddle one and fail for the right reasons. Walking three texels
     * in 300 steps must produce a handful of distinct matrices, not 300.
     */
    @Test
    void theEyeEntersOnlyInWholeTexels() {
        float texel = camera.texelWorldSize();
        int steps = 300;
        float span = texel * 3.0f;

        for (String axis : new String[] {"x", "y", "z"}) {
            int distinct = 0;
            Matrix4f previous = null;
            for (int i = 0; i <= steps; i++) {
                float d = span * i / steps;
                Matrix4f m =
                        switch (axis) {
                            case "x" -> matrixAt(700.0f + d, 96.0f, 190.0f);
                            case "y" -> matrixAt(700.0f, 96.0f + d, 190.0f);
                            default -> matrixAt(700.0f, 96.0f, 190.0f + d);
                        };
                if (previous == null || !previous.equals(m)) distinct++;
                previous = m;
            }
            // Travel along one WORLD axis moves the eye along all three LIGHT-space axes, and each
            // is snapped independently, so three texels of travel can cross a few cells on each.
            // A dozen or so is quantised; anything approaching `steps` means no snapping at all.
            assertTrue(
                    distinct <= 16,
                    "walking 3 texels along " + axis + " produced " + distinct + " distinct light matrices over "
                            + steps + " samples; the eye is not being snapped to the texel grid");
        }
    }

    /*
     * Standing still while the sun moves. The shadow map may only slide the way the sun is going —
     * a fixed world point's position in the map must not reverse direction over and over.
     *
     * Reported symptom: shadows moving "back and forth, up and down" as the day runs, which is not
     * something a sun crossing the sky can produce.
     */
    @Test
    void sunMoving_doesNotMakeTheMapOscillate() {
        Vector3f eye = new Vector3f(700.0f, 96.0f, 190.0f);
        // A fixed point on the ground near the player, tracked through the map.
        Vector3f probe = new Vector3f(710.0f, 96.0f, 200.0f);

        int reversals = 0;
        float previous = Float.NaN;
        float previousStep = 0.0f;
        // A 1200 s day at 60 fps: one frame is 0.3 degrees of sun travel.
        for (int frame = 0; frame < 240; frame++) {
            double angle = Math.toRadians(40.0 + frame * 0.3 / 60.0);
            camera.update(eye, new Vector3f(0.0f, (float) Math.sin(angle), (float) Math.cos(angle)).normalize());

            Vector3f ndc = camera.lightViewProj().transformPosition(new Vector3f(probe));
            float texelX = ndc.x * 1024.0f; // NDC -> texels across a 2048 map

            if (!Float.isNaN(previous)) {
                float step = texelX - previous;
                // A frame where nothing moved is the good case, not a reversal — only compare
                // frames that actually moved, or holding still scores worse than juddering.
                if (step != 0.0f) {
                    if (previousStep != 0.0f && Math.signum(step) != Math.signum(previousStep)) reversals++;
                    previousStep = step;
                }
            }
            previous = texelX;
        }

        assertTrue(
                reversals <= 4,
                "a fixed point reversed direction in the shadow map " + reversals
                        + " times over 240 frames of steady sun travel; the map is oscillating, not sliding");
    }

    /*
     * The other half of the same property. A snap that always returned the same matrix would pass
     * the test above and give you a shadow box that never follows the player at all.
     */
    @Test
    void largeMovement_doesMoveTheBox() {
        Matrix4f near = matrixAt(700.0f, 96.0f, 190.0f);
        Matrix4f far = matrixAt(700.0f + 50.0f, 96.0f, 190.0f);
        assertNotEquals(near, far, "the box has to follow the player at some scale");
    }

    /*
     * Sub-texel steps must not accumulate: walking a long way in tiny increments has to land on
     * the same grid as jumping straight there, or the map drifts off the world grid over a session.
     */
    @Test
    void manySubTexelSteps_landOnTheSameGridAsOneJump() {
        float texel = camera.texelWorldSize();
        float total = texel * 10.0f;

        Matrix4f direct = matrixAt(700.0f + total, 96.0f, 190.0f);

        Matrix4f stepped = null;
        for (int i = 1; i <= 100; i++) {
            stepped = matrixAt(700.0f + total * i / 100.0f, 96.0f, 190.0f);
        }
        assertEquals(direct, stepped, "the grid is anchored to the world, not to the walk");
    }

    /*
     * At noon the sun points straight down, which is parallel to the default up vector. lookAt
     * builds its basis from a cross product with up, and parallel inputs give the zero vector,
     * then NaN — a black screen with no error anywhere. ShadowCamera swaps the up vector for
     * exactly this case; this is the regression test for that swap.
     */
    @Test
    void sunDirectlyOverhead_producesAFiniteMatrix() {
        camera.update(new Vector3f(700.0f, 96.0f, 190.0f), new Vector3f(0.0f, 1.0f, 0.0f));

        float[] values = new float[16];
        camera.lightViewProj().get(values);
        for (int i = 0; i < values.length; i++) {
            assertTrue(Float.isFinite(values[i]), "element " + i + " is " + values[i] + " with the sun overhead");
        }
    }

    /*
     * A sun near the horizon must be lifted, so the box keeps looking down at terrain rather than
     * standing on its edge and spending most of its texels on empty sky.
     *
     * The clamp preserves azimuth — it lifts each sun in place rather than collapsing every low
     * sun onto one direction — so this checks the elevation that comes out, not equality between
     * two different low suns.
     */
    @Test
    void sunNearTheHorizon_isLiftedWellAboveIt() {
        camera.update(new Vector3f(0.0f, 96.0f, 0.0f), new Vector3f(1.0f, 0.02f, 0.0f).normalize());

        assertTrue(
                camera.effectiveSunDirection().y() > 0.3f,
                "a 1-degree sun must be lifted toward the floor, got elevation sin "
                        + camera.effectiveSunDirection().y());
    }

    /*
     * A sun above the floor must be left where it is, to within the quantisation step. It is not
     * exact any more: the sun is rounded to a fixed angular grid so the light's rotation holds
     * still between steps (see quantiseSunDirection). Half a degree of tolerance covers a quarter
     * degree step in either direction.
     */
    @Test
    void sunAboveTheFloor_isKeptWithinOneQuantisationStep() {
        camera.update(new Vector3f(0.0f, 96.0f, 0.0f), SUN);

        float cosAngle = SUN.dot(camera.effectiveSunDirection());
        float degreesOff = (float) Math.toDegrees(Math.acos(Math.min(1.0f, cosAngle)));
        assertTrue(degreesOff < 0.5f, "sun moved " + degreesOff + " degrees, more than one quantisation step");
    }

    /*
     * The point of quantising: between steps the matrix must be bit-identical, because that is
     * what stops the map re-rasterising every frame while the sun crosses the sky.
     */
    @Test
    void aSunNudgeSmallerThanAStep_leavesTheMatrixBitIdentical() {
        Vector3f eye = new Vector3f(700.0f, 96.0f, 190.0f);
        camera.update(eye, new Vector3f(0.0f, (float) Math.sin(0.7), (float) Math.cos(0.7)).normalize());
        Matrix4f reference = new Matrix4f(camera.lightViewProj());

        // A frame's worth of travel at DEFAULT_DAY_SECONDS is 0.005 degrees, far under one step.
        double nudged = 0.7 + Math.toRadians(0.005);
        camera.update(eye, new Vector3f(0.0f, (float) Math.sin(nudged), (float) Math.cos(nudged)).normalize());

        assertEquals(reference, camera.lightViewProj(), "one frame of sun travel must not move the shadow map");
    }
}
