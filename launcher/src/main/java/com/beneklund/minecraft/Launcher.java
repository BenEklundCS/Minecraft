package com.beneklund.minecraft;

import com.beneklund.minecraft.container.GameContainer;
import com.beneklund.minecraft.container.ServerContainer;
import com.beneklund.minecraft.net.InJvmLink;

/** Builds and runs the containers a {@link LaunchMode} calls for. */
final class Launcher {
    private static final int LOCAL_PLAYER_ID = 1;

    private Launcher() {}

    /**
     * Runs {@code mode} until the client window closes.
     *
     * @throws UnsupportedOperationException for {@link LaunchMode.Join} and {@link
     *     LaunchMode.Dedicated}, which need a socket transport
     */
    static void launch(LaunchMode mode) throws Exception {
        switch (mode) {
            case LaunchMode.Host host -> host(host);
            case LaunchMode.Join join -> throw new UnsupportedOperationException("Join needs a socket transport");
            case LaunchMode.Dedicated dedicated ->
                throw new UnsupportedOperationException("Dedicated needs a socket transport");
        }
    }

    /**
     * Starts the server on its own tick thread and runs the client on this thread, which owns GL,
     * joined by an {@link InJvmLink}. Stops the server however the client exits. The port is
     * unused until a socket transport exists.
     */
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
