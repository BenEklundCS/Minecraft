package com.beneklund.minecraft.platform.images;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public record ImageData(ByteBuffer pixels, int width, int height, int channels, Consumer<ImageData> onClose)
        implements AutoCloseable {
    @Override
    public void close() {
        onClose().accept(this);
    }
}
