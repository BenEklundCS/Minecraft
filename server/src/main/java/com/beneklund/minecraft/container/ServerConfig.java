package com.beneklund.minecraft.container;

/**
 * Server launch settings.
 *
 * <p>The spawn column ({@code spawnX}, {@code spawnZ}) applies only when there is no saved player;
 * the spawn height is measured from the generated terrain.
 */
public record ServerConfig(
        long seed,
        int loadRadius,
        float spawnX,
        float spawnZ,
        float spawnPitch,
        float spawnYaw,
        long shutdownTimeoutSeconds) {

    public static ServerConfig defaults() {
        return new ServerConfig(87L, 32, 8.0f, -5.0f, 20.0f, 0.0f, 5L);
    }
}
