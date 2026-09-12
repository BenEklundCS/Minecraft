package com.beneklund.minecraft;

import com.beneklund.minecraft.container.GameContainer;
import com.beneklund.minecraft.container.ServerContainer;
import com.beneklund.minecraft.net.InJvmLink;

final class Launcher {
    private static final int LOCAL_PLAYER_ID = 1;

    private Launcher() {}

    static void launch(LaunchMode mode) throws Exception {
        switch (mode) {
            case LaunchMode.Host host -> host(host);
            case LaunchMode.Join join -> throw new UnsupportedOperationException("Join needs a socket transport");
            case LaunchMode.Dedicated dedicated ->
                throw new UnsupportedOperationException("Dedicated needs a socket transport");
        }
    }

    // Server on its own thread, client on the main thread (GL), joined by an InJvmLink.
    // The port is unused until there's a socket transport for anyone else to connect over.
    private static void host(LaunchMode.Host host) throws Exception {
        InJvmLink.Pair link = InJvmLink.connect(LOCAL_PLAYER_ID);
        ServerContainer server = new ServerContainer(host.server());
        server.accept(link.client());
        server.start();
        try {
            new GameContainer(host.client(), link.server()).run();
        } finally {
            server.stop();
        }
    }
}
