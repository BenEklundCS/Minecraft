package com.beneklund.minecraft.platform.images;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StbImageLoaderTest {
    private final StbImageLoader loader = new StbImageLoader();

    @Test
    void load_validPng_returnsImageDataWithPositiveDimensions() {
        try (ImageData data = loader.load("/packs/faithful/textures/mcl_core_apple_golden.png")) {
            assertNotNull(data.pixels());
            assertTrue(data.width() > 0);
            assertTrue(data.height() > 0);
            assertTrue(data.channels() > 0);
            assertTrue(data.pixels().remaining() > 0);
        }
    }

    @Test
    void load_missingResource_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> loader.load("/does/not/exist.png"));
    }
}
