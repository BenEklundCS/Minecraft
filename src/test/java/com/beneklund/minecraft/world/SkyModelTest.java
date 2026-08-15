package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/*
 * sky.frag computes this same blend per pixel and Renderer samples colorFor() once per frame for
 * the fog colour. The two have to agree: fog is what distant terrain dissolves into, and the
 * horizon is where it meets the sky, so any drift between them shows up as a seam across the
 * whole frame. Nothing can test the GLSL side from here, so these pin the Java side hard enough
 * that a change to one without the other fails a test instead of only looking slightly wrong.
 */
public class SkyModelTest {
    private static final float TURBIDITY = 2.5f;
    private static final float EXPOSURE = 0.115f;

    // Both edges of the twilight fade, as sun altitudes (sunDirection.y). These mirror
    // NIGHT_BELOW and DAY_ABOVE in SkyModel, which are private, so they are a hand-kept copy:
    // change one and this file has to follow. The midpoint test below is what catches it —
    // smoothstep is symmetric about its own midpoint, so a stale edge here puts the expected
    // midpoint somewhere the model's curve does not cross 0.5.
    private static final float FULLY_NIGHT = -0.10f;
    private static final float FULLY_DAY = 0.0f;

    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Vector3f HORIZON = new Vector3f(0, 0, 1);

    @Test
    void dayFactor_isOneWhileTheSunIsUpAndZeroOnceItIsDown() {
        assertEquals(1.0f, sky(sunAt(FULLY_DAY)).dayFactor(), 1e-5);
        assertEquals(1.0f, sky(sunAt(1.0f)).dayFactor(), 1e-5);
        assertEquals(0.0f, sky(sunAt(FULLY_NIGHT)).dayFactor(), 1e-5);
        assertEquals(0.0f, sky(sunAt(-1.0f)).dayFactor(), 1e-5);
    }

    @Test
    void dayFactor_isHalfwayAtTheMidpointOfTwilight() {
        // smoothstep is symmetric about its midpoint, so this catches the edges being swapped -
        // which would otherwise read as a sky that is dark at noon and bright at midnight.
        float midpoint = (FULLY_NIGHT + FULLY_DAY) / 2.0f;
        assertEquals(0.5f, sky(sunAt(midpoint)).dayFactor(), 1e-5);
    }

    @Test
    void dayFactor_neverGoesBackwardsAsTheSunRises() {
        float previous = -1.0f;
        for (float y = -1.0f; y <= 1.0f; y += 0.01f) {
            float day = sky(sunAt(y)).dayFactor();
            assertTrue(day >= previous, "dayFactor dipped at sun altitude %f: %f < %f".formatted(y, day, previous));
            previous = day;
        }
    }

    @Test
    void colorFor_isExactlyTheNightGradientOnceTheSunIsDown() {
        // Preetham contributes nothing here, so these are the night constants unmodified. If the
        // blend ever leaks daylight into full night, the residual sunset glow is back.
        SkyModel night = sky(sunAt(-1.0f));
        assertVectorEquals(night.nightZenith(), night.colorFor(UP));
        assertVectorEquals(night.nightHorizon(), night.colorFor(HORIZON));
    }

    @Test
    void colorFor_isBrightAtNoonAndDarkAtMidnight() {
        Vector3f noon = sky(sunAt(1.0f)).colorFor(UP);
        Vector3f midnight = sky(sunAt(-1.0f)).colorFor(UP);
        assertTrue(
                noon.x > midnight.x && noon.y > midnight.y && noon.z > midnight.z,
                "noon %s vs midnight %s".formatted(noon, midnight));
        assertTrue(noon.y > 0.5f, "the zenith at noon should be a bright sky, got " + noon);
    }

    @Test
    void colorFor_staysInsideDisplayRange() {
        // colorFor returns post-exposure values that sky.frag writes straight to the framebuffer.
        // The tonemap is 1 - exp(-x), which cannot exceed 1 - so anything outside [0, 1] here
        // means a negative radiance reached it, which is the xyY conversion having gone wrong.
        for (float y = -1.0f; y <= 1.0f; y += 0.05f) {
            SkyModel model = sky(sunAt(y));
            for (Vector3f dir : new Vector3f[] {UP, HORIZON, sunAt(y)}) {
                Vector3f color = model.colorFor(dir);
                assertInRange(color.x, y, dir);
                assertInRange(color.y, y, dir);
                assertInRange(color.z, y, dir);
            }
        }
    }

    private static void assertInRange(float channel, float sunY, Vector3f dir) {
        assertTrue(
                channel >= 0.0f && channel <= 1.0f,
                "channel %f out of range looking at %s with the sun at %f".formatted(channel, dir, sunY));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-6, "red");
        assertEquals(expected.y, actual.y, 1e-6, "green");
        assertEquals(expected.z, actual.z, 1e-6, "blue");
    }

    // A sun direction at the given altitude, in the z plane the DayNightCycle actually swings through.
    private static Vector3f sunAt(float altitude) {
        float y = Math.max(-1.0f, Math.min(1.0f, altitude));
        float z = (float) Math.sqrt(Math.max(0.0f, 1.0f - y * y));
        return new Vector3f(0.0f, y, z).normalize();
    }

    private static SkyModel sky(Vector3f sunDirection) {
        return new SkyModel(TURBIDITY, EXPOSURE, sunDirection);
    }
}
