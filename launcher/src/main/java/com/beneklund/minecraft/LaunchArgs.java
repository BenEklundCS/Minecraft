package com.beneklund.minecraft;

import com.beneklund.minecraft.container.ClientConfig;
import com.beneklund.minecraft.container.ServerConfig;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class LaunchArgs {
    static final String NAME = "Minecraft";
    static final String ADDRESS = "address";
    static final int DEFAULT_PORT = 25565;
    static final int MIN_PORT = 1;
    static final int MAX_PORT = (1 << 16) - 1;

    record Endpoint(String host, int port) {}

    static LaunchMode parse(String[] args) throws ArgumentParserException {
        ArgumentParser parser = ArgumentParsers.newFor(NAME).build().defaultHelp(true);

        parser.addArgument("-j", "--join")
                .metavar("HOST[:PORT]")
                .help(String.format("join a server instead of hosting one; the port defaults to %d", DEFAULT_PORT))
                .dest(ADDRESS);

        Namespace res = parser.parseArgs(args);
        String address = res.getString(ADDRESS);
        if (address == null) {
            return new LaunchMode.Host(LaunchArgs.DEFAULT_PORT, ClientConfig.DEFAULT, ServerConfig.DEFAULT);
        }
        Endpoint endpoint;
        try {
            endpoint = endpoint(address);
        } catch (IllegalArgumentException e) {
            throw new ArgumentParserException(e.getMessage(), e, parser);
        }
        return new LaunchMode.Join(endpoint.host(), endpoint.port(), ClientConfig.DEFAULT);
    }

    static Endpoint endpoint(String address) {
        String[] parts = address.split(":");
        validateAddress(parts);
        String host = parts[0];
        int port = parts.length == 2 ? parsePort(parts[1]) : DEFAULT_PORT;
        validatePort(port);
        return new Endpoint(host, port);
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("invalid port '%s': not a number", text));
        }
    }

    static void validateAddress(String[] addressParts) {
        if (addressParts.length == 0 || addressParts.length > 2 || addressParts[0].isBlank()) {
            throw new IllegalArgumentException("invalid address");
        }
    }

    static void validatePort(int port) {
        if (port < MIN_PORT || port > MAX_PORT)
            throw new IllegalArgumentException(String.format("invalid port (%d <= port <= %d)", MIN_PORT, MAX_PORT));
    }
}
