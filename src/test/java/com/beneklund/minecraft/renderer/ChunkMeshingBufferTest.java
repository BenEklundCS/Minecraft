package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

// Small deliberate dimensions — a "quad" here is 4 vertices × 3 floats = 12 floats, and the
// buffer starts with room for 2 quads so the growth path is reachable in a test.
class ChunkMeshingBufferTest {
    private static final int FACES = 2;
    private static final int VERTS_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = 3;
    private static final int INDICES_PER_QUAD = 6;
    private static final int FLOATS_PER_QUAD = VERTS_PER_QUAD * FLOATS_PER_VERTEX; // 12

    private ChunkMeshingBuffer buffer() {
        return new ChunkMeshingBuffer(FACES, VERTS_PER_QUAD, FLOATS_PER_VERTEX, INDICES_PER_QUAD);
    }

    // Writes one whole quad's worth of vertex floats, values counting up from `from`.
    private static void writeQuad(ChunkMeshingBuffer buf, float from) {
        for (int i = 0; i < FLOATS_PER_QUAD; i++) buf.writeVert(from + i);
    }

    @Test
    void newBuffer_isEmpty() {
        ChunkMeshingBuffer buf = buffer();

        assertEquals(0, buf.base());
        assertEquals(0, buf.copyVertices().length, "capacity is not content");
        assertEquals(0, buf.copyIndices().length);
    }

    // The invariant advance() exists to enforce: one quad is exactly
    // verticesPerQuad × floatsPerVertex writes.
    @Test
    void advance_afterAFullQuad_movesTheVertexBase() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        writeQuad(buf, 0);

        buf.advance();

        assertEquals(VERTS_PER_QUAD, buf.base());
    }

    @Test
    void advance_afterTooFewFloats_throws() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        for (int i = 0; i < FLOATS_PER_QUAD - 1; i++) buf.writeVert(1f);

        IllegalStateException e = assertThrows(IllegalStateException.class, buf::advance);

        assertTrue(e.getMessage().contains(String.valueOf(FLOATS_PER_QUAD)), "message names the expected count");
        assertTrue(e.getMessage().contains(String.valueOf(FLOATS_PER_QUAD - 1)), "message names the actual count");
    }

    @Test
    void advance_afterTooManyFloats_throws() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        writeQuad(buf, 0);
        buf.writeVert(99f);

        assertThrows(IllegalStateException.class, buf::advance);
    }

    // The one that catches a quadStart that never moves: quad 2 measures itself from the start
    // of the buffer instead of the start of the quad, so it reads as double-length and throws.
    @Test
    void advance_isMeasuredPerQuad_notFromTheStartOfTheBuffer() {
        ChunkMeshingBuffer buf = buffer();

        for (int quad = 0; quad < 3; quad++) {
            buf.ensureQuadCapacity();
            writeQuad(buf, quad * FLOATS_PER_QUAD);
            buf.advance();
        }

        assertEquals(3 * VERTS_PER_QUAD, buf.base(), "three quads advanced");
    }

    // base() is what fillBufferIdxs offsets its six indices by, so it has to be the count of
    // vertices already written — not floats, and not quads.
    @Test
    void base_countsVerticesNotFloatsOrQuads() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        writeQuad(buf, 0);
        buf.advance();
        buf.ensureQuadCapacity();
        writeQuad(buf, FLOATS_PER_QUAD);
        buf.advance();

        assertEquals(8, buf.base());
    }

    @Test
    void copyVertices_returnsOnlyWhatWasWritten() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        writeQuad(buf, 0);
        buf.advance();

        float[] verts = buf.copyVertices();

        assertEquals(FLOATS_PER_QUAD, verts.length, "trimmed to vertPos, not the backing array");
        for (int i = 0; i < FLOATS_PER_QUAD; i++) assertEquals(i, verts[i], 1e-6);
    }

    @Test
    void copyIndices_returnsOnlyWhatWasWritten() {
        ChunkMeshingBuffer buf = buffer();
        buf.ensureQuadCapacity();
        for (int i : new int[] {0, 1, 2, 2, 3, 0}) buf.writeIdx(i);

        assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0}, buf.copyIndices());
    }

    // Growth has to preserve everything already written — a doubling that dropped content
    // would show up as garbage geometry rather than as an exception.
    @Test
    void growth_pastInitialCapacity_keepsEarlierQuads() {
        ChunkMeshingBuffer buf = buffer();
        int quads = FACES * 3; // forces at least two doublings

        for (int quad = 0; quad < quads; quad++) {
            buf.ensureQuadCapacity();
            writeQuad(buf, quad * FLOATS_PER_QUAD);
            for (int i = 0; i < INDICES_PER_QUAD; i++) buf.writeIdx(buf.base() + i);
            buf.advance();
        }

        float[] verts = buf.copyVertices();
        assertEquals(quads * FLOATS_PER_QUAD, verts.length);
        for (int i = 0; i < verts.length; i++) assertEquals(i, verts[i], 1e-6, "float " + i + " survived growth");
        assertEquals(quads * INDICES_PER_QUAD, buf.copyIndices().length);
        assertEquals(quads * VERTS_PER_QUAD, buf.base());
    }

    // ensureQuadCapacity grows by exactly one quad's headroom, so calling it once per quad is
    // the contract. Writing a quad without it is what an out-of-bounds looks like today.
    @Test
    void writingBeyondCapacity_withoutEnsure_throws() {
        ChunkMeshingBuffer buf = buffer();
        for (int quad = 0; quad < FACES; quad++) {
            writeQuad(buf, quad * FLOATS_PER_QUAD);
            buf.advance();
        }

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> writeQuad(buf, 0));
    }
}
