package com.beneklund.minecraft.platform.images;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A decoded image whose pixels live in native memory. Close it once the pixels are uploaded: the
 * garbage collector never frees off-heap memory, so {@code onClose} hands the buffer back to the
 * allocator that made it.
 *
 * @param channels channel count in the source file; {@code pixels} holds whatever the loader
 *     requested
 */
public record ImageData(ByteBuffer pixels, int width, int height, int channels, Consumer<ImageData> onClose)
        implements AutoCloseable {
    @Override
    public void close() {
        onClose().accept(this);
    }
}
