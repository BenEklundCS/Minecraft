package com.beneklund.minecraft;

import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.container.ClientContainer;
import com.beneklund.minecraft.container.ServerContainer;
import com.beneklund.minecraft.net.IServerLink;
import com.beneklund.minecraft.net.InJvmLink;
import com.beneklund.minecraft.net.PacketCodec;
import com.beneklund.minecraft.net.SocketServerLink;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Builds and runs the containers a {@link LaunchMode} calls for. */
final class Launcher {
    private static final int LOCAL_PLAYER_ID = 1;
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    private Launcher() {}

    /**
     * Runs {@code mode} until the client window closes.
     *
     * @throws UnsupportedOperationException for {@link LaunchMode.Dedicated}, which isn't wired up
     *     yet
     */
    static void launch(LaunchMode mode) throws Exception {
        switch (mode) {
            case LaunchMode.Host host -> host(host);
            case LaunchMode.Join join -> join(join);
            case LaunchMode.Dedicated dedicated ->
                throw new UnsupportedOperationException("Dedicated isn't wired up yet");
        }
    }

    /**
     * Starts the server on its own tick thread and runs the client on this thread, which owns GL,
     * joined by an {@link InJvmLink}. Remote players reach the same server through the port. Stops
     * the server however the client exits.
     */
    private static void host(LaunchMode.Host host) throws Exception {
        InJvmLink.Pair link = InJvmLink.connect(LOCAL_PLAYER_ID);
        ServerContainer server = new ServerContainer(host.server());
        server.accept(link.client());
        server.start();
        try {
            // Inside the try so a port that is already taken still stops the server it started.
            server.listen(host.port());
            new ClientContainer(host.client(), link.server()).run();
        } finally {
            server.stop();
        }
    }

    /**
     * Connects to a remote server and runs the client on this thread. No server in this JVM.
     *
     * <p>The connect has its own timeout because {@code new Socket(host, port)} waits on the OS,
     * which is minutes when a firewall drops the packets instead of refusing them.
     */
    private static void join(LaunchMode.Join join) throws Exception {
        Socket socket = new Socket();
        IServerLink link;
        try {
            socket.connect(new InetSocketAddress(join.host(), join.port()), CONNECT_TIMEOUT_MS);
            link = new SocketServerLink(socket, new PacketCodec(BlockRegistry.createDefault()));
        } catch (IOException e) {
            // The link never took the socket, so nothing else will close it.
            socket.close();
            throw e;
        }
        // From here the link owns the socket: closing the link lets its writer flush the client's
        // Disconnect before the socket shuts.
        try {
            new ClientContainer(join.client(), link).run();
        } finally {
            link.close();
        }
    }
}
