package com.beneklund.minecraft.platform.audio;

import java.nio.ShortBuffer;

/**
 * Decoded, interleaved 16-bit PCM in native memory. Close it once the samples are copied into an
 * OpenAL buffer; the garbage collector never frees off-heap memory.
 */
public record AudioData(ShortBuffer pcm, int channels, int sampleRate, Runnable onClose) implements AutoCloseable {
    @Override
    public void close() {
        onClose.run();
    }
}
