package com.beneklund.minecraft.container;

import com.beneklund.minecraft.util.Color;
import org.joml.Vector3f;

/**
 * Launch settings for the client: window, camera, resource pack and player tuning. {@link
 * #defaults()} is the one place to change what a plain launch looks like.
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

    public static ContainerConfig defaults() {
        return new ContainerConfig(
                "Minecraft",
                1200,
                800,
                false,
                WindowConfig.Mode.WINDOWED,
                Color.FOG,
                70.0f,
                87L,
                32,
                "/packs/faithful/pack.json",
                new PlayerConfig(new Vector3f(8.0f, 75.0f, -5.0f), 20.0f, 0.0f, 4.3f, 8.4f, 8.0f),
                5L);
    }
}
