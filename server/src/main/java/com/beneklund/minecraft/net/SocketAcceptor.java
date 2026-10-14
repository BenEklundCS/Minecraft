package com.beneklund.minecraft.net;

import static com.beneklund.minecraft.util.Log.IO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts socket connections and hands each server-side link to the game server.
 *
 * <p>The listening socket is bound by the constructor, so an unavailable port fails during
 * startup. Once started, a daemon thread blocks in {@code accept()} and forwards each connection
 * through {@link IGameServer#accept(IClientLink)}. The game server queues links there and creates
 * their player sessions during its next tick; the accept thread never touches simulation state.
 *
 * <p>Socket-assigned player IDs start at {@code 2}. The in-JVM player already uses ID {@code 1},
 * and keeping allocation here gives socket connections one monotonically increasing ID source.
 * Closing the acceptor closes the listening socket, which unblocks the accept call and lets the
 * daemon thread exit.
 */
public class SocketAcceptor {
    private final ServerSocket socket;
    private final int port;
    private final PacketCodec codec;
    private final IGameServer server;

    private final AtomicInteger nextId;
    private Thread thread;

    /** Binds the listening socket for {@code port}; use {@code 0} to request an available port. */
    public SocketAcceptor(int port, PacketCodec codec, IGameServer server) throws IOException {
        socket = new ServerSocket(port);
        this.port = socket.getLocalPort();
        this.codec = codec;
        this.server = server;
        nextId = new AtomicInteger(2);
    }

    /** Starts the daemon thread that accepts connections and forwards them to the game server. */
    public void start() {
        Thread t = new Thread(this::acceptor, "net-accept");
        t.setDaemon(true);
        t.start();
        thread = t;
    }

    /** Closes the listening socket and unblocks the accept loop. */
    public void close() throws IOException {
        socket.close();
    }

    /** Returns the port assigned to the listening socket, including the OS-assigned port for 0. */
    public int port() {
        return port;
    }

    /** Continuously accepts new clients on the server, producing accepted {@code SocketClientLink} and assigning them an atomic player id. */
    private void acceptor() {
        while (true) {
            Socket s;
            try {
                s = socket.accept();
            } catch (IOException e) {
                // close() unblocks accept() with an exception too; only the other kind is news.
                if (!socket.isClosed()) IO.error("Listener is broken!", e);
                return;
            }
            try {
                server.accept(new SocketClientLink(s, codec, nextId.getAndIncrement()));
            } catch (IOException e) {
                IO.debug("This connection failed during setup: ", e);
                // The link never took ownership, so nothing else will close this socket.
                try {
                    s.close();
                } catch (IOException ignored) {
                    // already gone
                }
            }
        }
    }
}
