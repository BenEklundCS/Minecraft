package com.beneklund.minecraft.platform.audio;

import java.nio.ShortBuffer;

// Decoded audio in native memory. Must be closed after uploading to an AL buffer —
// the PCM data lives off-heap and won't be collected by the GC.
public record AudioData(ShortBuffer pcm, int channels, int sampleRate, Runnable onClose) implements AutoCloseable {
    @Override
    public void close() {
        this.onClose.run();
    }
}
