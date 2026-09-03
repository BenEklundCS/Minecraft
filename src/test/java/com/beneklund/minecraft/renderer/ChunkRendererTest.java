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
 * The radii come from ShadowCamera: casterRadius(i) is BOX_HALVES[i] + 128 over {32, 128, 512},
 * so 160, 256 and 640 blocks.
 */
class ChunkRendererTest {

    // A box sitting `distance` blocks away on +X, far enough off in Y to prove Y is not consulted.
    private static AABB boxAt(float distance) {
        return new AABB(distance, -64.0f, 0.0f, distance + 16.0f, 320.0f, 16.0f);
    }

    private static final Vector3f EYE = new Vector3f(0.0f, 70.0f, 8.0f);

    @Test
    void cascadeMaskAt100Blocks() {
        // 100 <= 160, 100 <= 256, 100 <= 640 - inside all three.
        assertEquals(0b111, ChunkRenderer.cascadeMaskFor(boxAt(100.0f), EYE));
    }

    @Test
    void cascadeMaskAt400Blocks() {
        // 400 > 160, 400 > 256, 400 <= 640 - the far cascade only.
        assertEquals(0b100, ChunkRenderer.cascadeMaskFor(boxAt(400.0f), EYE));
    }

    @Test
    void everyLoadedChunkClearsTheFarCascade() {
        // Render distance 32 puts the farthest loaded chunk 32 * 16 = 512 blocks out, and
        // casterRadius(2) is 640. So the mask is never zero for a loaded chunk, which means the
        // `if (cascades != 0)` guard in getDrawCalls rejects nothing at this render distance -
        // the fact Stage 8 is built on.
        assertNotEquals(0, ChunkRenderer.cascadeMaskFor(boxAt(512.0f), EYE));
    }

    @Test
    void maskIgnoresHeightEntirely() {
        // Same X/Z separation, wildly different Y. cascadeMaskFor measures on X and Z only, so a
        // chunk directly overhead casts into the same cascades as one at the player's feet.
        AABB low = new AABB(100.0f, 0.0f, 0.0f, 116.0f, 16.0f, 16.0f);
        AABB high = new AABB(100.0f, 2048.0f, 0.0f, 116.0f, 2064.0f, 16.0f);
        assertEquals(ChunkRenderer.cascadeMaskFor(low, EYE), ChunkRenderer.cascadeMaskFor(high, EYE));
    }
}
