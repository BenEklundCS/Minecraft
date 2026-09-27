package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ContainerConfig;
import com.beneklund.minecraft.container.ServerConfig;

/**
 * Entry point: hosts a local server with the default configs. Uses Java 21's preview instance
 * {@code main} (JEP 445), hence {@code --enable-preview}.
 *
 * @see <a href="https://openjdk.org/jeps/445">JEP 445: Unnamed Classes and Instance Main
 *     Methods</a>
 */
class Main {
    private static final int DEFAULT_PORT = 25565;

    void main() throws Exception {
        Launcher.launch(new LaunchMode.Host(DEFAULT_PORT, ContainerConfig.DEFAULT, ServerConfig.DEFAULT));
    }
}
