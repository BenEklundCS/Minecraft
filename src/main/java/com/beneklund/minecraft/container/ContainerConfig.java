package com.beneklund.minecraft.container;

import com.beneklund.minecraft.util.Color;
import org.joml.Vector3f;

public record ContainerConfig(
        String windowTitle,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        Color clearColor,
        float fov,
        long seed,
        int renderDistance,
        String resourcePack,
        PlayerConfig player,
        long shutdownTimeoutSeconds) {

    public static ContainerConfig defaults() {
        return new ContainerConfig(
                "Minecraft",
                1200,
                800,
                false,
                Color.SKY,
                70.0f,
                42L,
                4,
                "/packs/faithful/pack.json",
                new PlayerConfig(new Vector3f(8.0f, 75.0f, -5.0f), 20.0f, 0.0f, 4.3f, 8.4f, 8.0f),
                5L);
    }
}
