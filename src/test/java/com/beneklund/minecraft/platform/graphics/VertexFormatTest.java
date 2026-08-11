package com.beneklund.minecraft.platform.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class VertexFormatTest {
    @Test
    public void vertexFormatLineTest() {
        VertexFormat line = VertexFormat.LINE;
        assertEquals(6, line.floatsPerVertex());
        assertEquals(24, line.stride());
        assertEquals(0L, line.offsetOf(0));
        assertEquals(12L, line.offsetOf(1));
    }

    @Test
    public void vertexFormatHudHotbarTest() {
        VertexFormat hud = VertexFormat.HUD;
        assertEquals(32, hud.stride());
        assertEquals(0, hud.offsetOf(0));
        assertEquals(8, hud.offsetOf(1));
        assertEquals(24, hud.offsetOf(2));
    }

    @Test
    public void vertexFormatChunkTest() {
        VertexFormat chunk = VertexFormat.CHUNK;
        assertEquals(40, chunk.stride());
        assertEquals(0, chunk.offsetOf(0));
        assertEquals(12, chunk.offsetOf(1));
        assertEquals(20, chunk.offsetOf(2));
        assertEquals(24, chunk.offsetOf(3));
        assertEquals(28, chunk.offsetOf(4));
    }
}
