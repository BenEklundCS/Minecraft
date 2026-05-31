package com.beneklund.minecraft.container;

import com.beneklund.minecraft.util.Color;

public record WindowConfig(String title, int width, int height, boolean vsync, Color clearColor) {}
