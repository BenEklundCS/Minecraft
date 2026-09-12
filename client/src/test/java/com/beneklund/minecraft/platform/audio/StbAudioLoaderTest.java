package com.beneklund.minecraft.platform.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StbAudioLoaderTest {
    private final StbAudioLoader loader = new StbAudioLoader();

    @Test
    void load_validOgg_returnsDecodedPcmWithSaneMetadata() {
        try (AudioData data = loader.load("/sounds/default_chest_close.ogg")) {
            assertNotNull(data.pcm());
            assertTrue(data.pcm().remaining() > 0);
            assertTrue(data.channels() == 1 || data.channels() == 2);
            assertTrue(data.sampleRate() > 0);
        }
    }

    @Test
    void load_missingResource_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> loader.load("/does/not/exist.ogg"));
    }
}
