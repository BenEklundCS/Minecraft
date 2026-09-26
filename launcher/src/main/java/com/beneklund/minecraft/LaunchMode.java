package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ContainerConfig;
import com.beneklund.minecraft.container.ServerConfig;

/**
 * How a process runs the game. Every mode goes through a server; singleplayer is {@link Host} with
 * nobody else connected.
 */
public sealed interface LaunchMode {
    /** A server and a local client in one JVM. The only mode {@code Launcher} supports so far. */
    record Host(int port, ContainerConfig client, ServerConfig server) implements LaunchMode {}

    /** A client connecting to a remote server. Needs a socket transport. */
    record Join(String host, int port, ContainerConfig client) implements LaunchMode {}

    /** A server with no local client. Needs a socket transport. */
    record Dedicated(int port, ServerConfig server) implements LaunchMode {}
}
