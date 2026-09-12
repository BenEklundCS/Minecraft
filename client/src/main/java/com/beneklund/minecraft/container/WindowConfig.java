package com.beneklund.minecraft.container;

import com.beneklund.minecraft.util.Color;

public record WindowConfig(
        String title, int width, int height, boolean vsync, Mode mode, Color clearColor, boolean debugEnabled) {
    public enum Mode {
        WINDOWED,
        WINDOWED_FULLSCREEN,
        FULLSCREEN;

        public boolean fullscreen() {
            return this == FULLSCREEN || this == WINDOWED_FULLSCREEN;
        }
    }
}
