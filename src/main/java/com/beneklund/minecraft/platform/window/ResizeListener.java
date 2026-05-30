package com.beneklund.minecraft.platform.window;

@FunctionalInterface
public interface ResizeListener {
    void onResize(int width, int height);
}
