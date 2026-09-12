package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ContainerConfig;
import com.beneklund.minecraft.container.ServerConfig;

// Every mode goes through a server. Playing alone is Host with nobody else connected.
public sealed interface LaunchMode {
    record Host(int port, ContainerConfig client, ServerConfig server) implements LaunchMode {}

    record Join(String host, int port, ContainerConfig client) implements LaunchMode {}

    record Dedicated(int port, ServerConfig server) implements LaunchMode {}
}
