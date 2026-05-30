package com.beneklund.minecraft.platform.audio;

import java.nio.ShortBuffer;

public record AudioData(ShortBuffer pcm, int channels, int sampleRate, Runnable onClose) implements AutoCloseable {
    @Override
    public void close() {
        this.onClose.run();
    }
}
