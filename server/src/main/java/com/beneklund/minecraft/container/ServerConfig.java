package com.beneklund.minecraft.container;

// The spawn column is used only when there's no saved player; its height is measured.
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
