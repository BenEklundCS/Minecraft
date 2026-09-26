package com.beneklund.minecraft.platform.window;

/** Receives the new framebuffer size in pixels, on the main thread during event polling. */
@FunctionalInterface
public interface IResizeListener {
    void onResize(int width, int height);
}
