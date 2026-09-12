package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ContainerConfig;
import com.beneklund.minecraft.container.ServerConfig;

class Main {
    private static final int DEFAULT_PORT = 25565;

    void main() throws Exception {
        Launcher.launch(new LaunchMode.Host(DEFAULT_PORT, ContainerConfig.defaults(), ServerConfig.defaults()));
    }
}
