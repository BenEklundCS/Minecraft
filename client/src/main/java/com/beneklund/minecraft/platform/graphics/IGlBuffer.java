package com.beneklund.minecraft.platform.graphics;

/** A GL buffer object bound to a fixed target. */
public interface IGlBuffer {
    /** Binds this buffer to its target. */
    void bind();

    /** Releases the GL buffer name and its storage. */
    void delete();
}
