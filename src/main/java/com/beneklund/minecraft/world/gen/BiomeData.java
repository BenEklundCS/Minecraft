package com.beneklund.minecraft.world.gen;

import com.beneklund.minecraft.util.Color;

public record BiomeData(
        int baseHeight,
        int amplitude,
        Color grassColor,
        Color foliageColor
) {
}
