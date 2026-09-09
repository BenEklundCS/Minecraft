package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.beneklund.minecraft.util.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/*
 * cascadeMaskFor is the one piece of ChunkRenderer that can be tested without a GL context - it
 * takes an AABB and an eye position and consults nothing else. That matters because the shadow
 * draw-call counts rest entirely on this method ignoring the frustum: looking at the sky must
 * not change the shadow draw-call count, and this is why.
 *
 * The radii come from ShadowCamera and now depend on the sun. The box halves are {32, 128, 512};
 * the margin on top is shadowReach(sun) scaled by how far up-sun the chunk sits, capped at 128.
 * With a low sun directly behind the chunk that reproduces the old flat 160/256/640.
 */
class ChunkRendererTest {

    // A box sitting `distance` blocks away on +X, far enough off in Y to prove Y is not consulted.
    private static AABB boxAt(float distance) {
        return new AABB(distance, -64.0f, 0.0f, distance + 16.0f, 320.0f, 16.0f);
    }

    private static final Vector3f EYE = new Vector3f(0.0f, 70.0f, 8.0f);

    // Low sun on +X, the side boxAt() puts its chunks on: reach is capped at 128 and upSun is 1,
    // so these are the old flat radii and the pre-existing cases below still mean what they did.
    private static final Vector3f LOW_SUN_UPSUN = new Vector3f(0.995f, 0.1f, 0.0f);

    // Same elevation, opposite side. The chunk is down-sun, its shadow falls away from the box,
    // so it gets no margin at all.
    private static final Vector3f LOW_SUN_DOWNSUN = new Vector3f(-0.995f, 0.1f, 0.0f);

    // Overhead. Shadows fall straight down, reach is ~0, and only the bare box counts.
    private static final Vector3f NOON = new Vector3f(0.0f, 1.0f, 0.0f);

    @Test
    void cascadeMaskAt100Blocks() {
        // 100 <= 160, 100 <= 256, 100 <= 640 - inside all three.
        assertEquals(0b111, ChunkRenderer.cascadeMaskFor(boxAt(100.0f), EYE, LOW_SUN_UPSUN));
    }

    @Test
    void cascadeMaskAt400Blocks() {
        // 400 > 160, 400 > 256, 400 <= 640 - the far cascade only.
        assertEquals(0b100, ChunkRenderer.cascadeMaskFor(boxAt(400.0f), EYE, LOW_SUN_UPSUN));
    }

    @Test
    void everyLoadedChunkClearsTheFarCascade() {
        // Render distance 32 puts the farthest loaded chunk 32 * 16 = 512 blocks out, and
        // casterRadius(2) is 640. So the mask is never zero for a loaded chunk, which means the
        // `if (cascades != 0)` guard in getDrawCalls rejects nothing at this render distance -
        // the fact Stage 8 is built on.
        assertNotEquals(0, ChunkRenderer.cascadeMaskFor(boxAt(512.0f), EYE, LOW_SUN_UPSUN));
    }

    @Test
    void maskIgnoresHeightEntirely() {
        // Same X/Z separation, wildly different Y. cascadeMaskFor measures on X and Z only, so a
        // chunk directly overhead casts into the same cascades as one at the player's feet.
        AABB low = new AABB(100.0f, 0.0f, 0.0f, 116.0f, 16.0f, 16.0f);
        AABB high = new AABB(100.0f, 2048.0f, 0.0f, 116.0f, 2064.0f, 16.0f);
        assertEquals(
                ChunkRenderer.cascadeMaskFor(low, EYE, LOW_SUN_UPSUN),
                ChunkRenderer.cascadeMaskFor(high, EYE, LOW_SUN_UPSUN));
    }

    @Test
    void downSunChunkLosesTheMargin() {
        // 600 blocks out on +X. Up-sun it is inside casterRadius(2) = 512 + 128; down-sun the
        // margin is gone and 600 > 512, so the far cascade drops it. Same chunk, same sun
        // elevation, opposite bearing - this is the whole point of the direction term.
        AABB far = boxAt(600.0f);
        assertNotEquals(0, ChunkRenderer.cascadeMaskFor(far, EYE, LOW_SUN_UPSUN));
        assertEquals(0, ChunkRenderer.cascadeMaskFor(far, EYE, LOW_SUN_DOWNSUN));
    }

    @Test
    void noonDropsTheMarginEvenUpSun() {
        // With the sun overhead there is no horizontal reach, so the 128 is not earned at any
        // bearing. 600 > 512 and the chunk falls out of every cascade.
        assertEquals(0, ChunkRenderer.cascadeMaskFor(boxAt(600.0f), EYE, NOON));
    }

    @Test
    void insideTheBoxSurvivesEverySun() {
        // 400 blocks is inside c2's bare 512 box, so no sun position can reject it. Guards the
        // failure that matters: an over-eager test deleting shadows that were correct.
        AABB inside = boxAt(400.0f);
        for (Vector3f sun : new Vector3f[] {LOW_SUN_UPSUN, LOW_SUN_DOWNSUN, NOON}) {
            assertNotEquals(0, ChunkRenderer.cascadeMaskFor(inside, EYE, sun), "sun " + sun);
        }
    }
}
