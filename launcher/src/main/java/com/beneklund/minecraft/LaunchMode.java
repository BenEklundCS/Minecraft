package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ClientConfig;
import com.beneklund.minecraft.container.ServerConfig;

/**
 * How a process runs the game. Every mode goes through a server; singleplayer is {@link Host} with
 * nobody else connected.
 */
public sealed interface LaunchMode {
    /** A server and a local client in one JVM, listening on {@code port} for remote players. */
    record Host(int port, ClientConfig client, ServerConfig server) implements LaunchMode {}

    /** A client connecting to a remote server, with no server in its own JVM. */
    record Join(String host, int port, ClientConfig client) implements LaunchMode {}

    /** A server with no local client. Not wired up in {@code Launcher} yet. */
    record Dedicated(int port, ServerConfig server) implements LaunchMode {}
}
