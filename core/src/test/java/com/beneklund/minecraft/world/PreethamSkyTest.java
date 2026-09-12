package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/*
 * These are transcription tests, not behaviour tests. The Preetham coefficients are fifteen
 * hand-copied constants plus two cubics, and a single wrong digit produces a sky that renders
 * fine and looks subtly wrong - which is very expensive to debug through the GPU. The values
 * below were computed by hand from the paper, so they fail loudly if a digit moved.
 */
public class PreethamSkyTest {
    private static final float CLEAR_DAY = 2.0f;

    private static final Vector3f SUN_OVERHEAD = new Vector3f(0, 1, 0);
    private static final Vector3f SUN_ON_HORIZON = new Vector3f(0, 0, 1);
    private static final Vector3f SUN_BELOW = new Vector3f(0, -1, 0);

    @Test
    void zenithLuminance_matchesHandComputedValues() {
        assertEquals(15.5f, sky(CLEAR_DAY, SUN_OVERHEAD).zenithLuminance(), 0.1f);
        assertEquals(1.99f, sky(CLEAR_DAY, SUN_ON_HORIZON).zenithLuminance(), 0.01f);
    }

    @Test
    void zenithLuminance_risesWithTurbidity() {
        float clear = sky(2.0f, SUN_OVERHEAD).zenithLuminance();
        float hazy = sky(6.0f, SUN_OVERHEAD).zenithLuminance();
        assertTrue(hazy > clear, "haze scatters more light toward the zenith, got %f vs %f".formatted(hazy, clear));
    }

    @Test
    void zenithChromaticity_matchesHandComputedValues() {
        // With the sun overhead thetaS is 0, so every cubic term drops out and only the
        // constant and linear-in-T terms survive: xz = 2*0.00394 + 0.25886.
        PreethamSky noon = sky(CLEAR_DAY, SUN_OVERHEAD);
        assertEquals(0.26674f, noon.zenithX(), 1e-4);
        assertEquals(0.27720f, noon.zenithY(), 1e-4);
    }

    @Test
    void thetaS_clampsAtTheHorizon() {
        // Preetham is a daylight model. Past sunset the raw angle would exceed PI/2 and drive
        // zenith luminance negative, so the sun is held at the horizon and SkyModel.dayFactor()
        // fades the model out into night instead (see SkyModelTest).
        assertEquals((float) (Math.PI / 2.0), sky(CLEAR_DAY, SUN_BELOW).thetaS(), 1e-5);
        assertTrue(sky(CLEAR_DAY, SUN_BELOW).zenithLuminance() > 0.0f, "night must not produce negative luminance");
    }

    @Test
    void zenithF_isFiniteAndPositive() {
        // The normalisation divisor. A zero or NaN here silently blanks the whole sky.
        for (Vector3f sun : new Vector3f[] {SUN_OVERHEAD, SUN_ON_HORIZON, SUN_BELOW}) {
            Vector3f f = sky(CLEAR_DAY, sun).zenithF();
            assertTrue(Float.isFinite(f.x) && f.x > 0.0f, "Y divisor: " + f.x);
            assertTrue(Float.isFinite(f.y) && f.y > 0.0f, "x divisor: " + f.y);
            assertTrue(Float.isFinite(f.z) && f.z > 0.0f, "y divisor: " + f.z);
        }
    }

    @Test
    void coefficients_areOrderedYThenXThenY() {
        // Each accessor packs (luminance Y, chromatic x, chromatic y) to match the vec3
        // uniforms. At T=2: A_Y = 0.1787*2 - 1.4630, A_x = -0.0193*2 - 0.2592, and so on.
        Vector3f a = sky(CLEAR_DAY, SUN_OVERHEAD).coefficientA();
        assertEquals(-1.1056f, a.x, 1e-4);
        assertEquals(-0.2978f, a.y, 1e-4);
        assertEquals(-0.2942f, a.z, 1e-4);
    }

    private static PreethamSky sky(float turbidity, Vector3f sunDirection) {
        return new PreethamSky(turbidity, sunDirection);
    }
}
