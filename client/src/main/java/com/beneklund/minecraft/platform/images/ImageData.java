package com.beneklund.minecraft.platform.images;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

// STB-decoded image in native memory. Must be closed after uploading to a GL texture —
// pixels is an off-heap ByteBuffer that stbi_image_free must reclaim.
public record ImageData(ByteBuffer pixels, int width, int height, int channels, Consumer<ImageData> onClose)
        implements AutoCloseable {
    @Override
    public void close() {
        onClose().accept(this);
    }
}
