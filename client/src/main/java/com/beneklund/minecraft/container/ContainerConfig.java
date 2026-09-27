package com.beneklund.minecraft.container;

import com.beneklund.minecraft.util.Color;

/**
 * Launch settings for the client: window, camera, resource pack and player tuning. {@link
 * #DEFAULT} is the one place to change what a plain launch looks like.
 *
 * <p>{@code seed} and {@code renderDistance} are only logged here. The world is the server's, so
 * {@code ServerConfig} owns the seed and the load radius that decide what gets streamed.
 *
 * @param fov vertical field of view in degrees
 * @param resourcePack classpath path to the pack's {@code pack.json}
 * @param shutdownTimeoutSeconds how long shutdown waits for the meshing pool to drain
 */
public record ContainerConfig(
        String windowTitle,
        int windowWidth,
        int windowHeight,
        boolean vsync,
        WindowConfig.Mode mode,
        Color clearColor,
        float fov,
        long seed,
        int renderDistance,
        String resourcePack,
        PlayerConfig player,
        long shutdownTimeoutSeconds) {

    public static final ContainerConfig DEFAULT = new ContainerConfig(
            "Minecraft",
            1200,
            800,
            false,
            WindowConfig.Mode.WINDOWED,
            Color.FOG,
            70.0f,
            87L,
            12,
            "/packs/faithful/pack.json",
            PlayerConfig.DEFAULT,
            5L);
}
