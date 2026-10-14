package com.beneklund.minecraft.net;

import java.net.Socket;
import java.net.SocketException;
import java.util.List;

/**
 * The client-facing adapter for one socket connection.
 *
 * <p>A {@link SocketConnection} handles socket I/O on daemon threads. The client polls {@link
 * #drain()} and queues packets with {@link #send(IPacket.ToServer)}; neither operation waits for
 * the peer. Closing the link preserves packets already queued for sending and packets already
 * received for draining.
 */
public class SocketServerLink implements IServerLink {
    private static final String name = "server";
    private final SocketConnection<IPacket.ToClient, IPacket.ToServer> conn;

    public SocketServerLink(Socket socket, PacketCodec codec) throws SocketException {
        conn = new SocketConnection<>(socket, codec, IPacket.ToClient.class, name);
    }

    /** Queues a packet for the server; returns without waiting for socket I/O. */
    @Override
    public void send(IPacket.ToServer packet) {
        conn.send(packet);
    }

    /** Returns packets received from the server since the previous call, in arrival order. */
    @Override
    public List<IPacket.ToClient> drain() {
        return conn.drain();
    }

    /** Returns whether the connection is still open. */
    @Override
    public boolean isOpen() {
        return conn.isOpen();
    }

    /** Closes the connection after queued outbound packets have been written. */
    @Override
    public void close() {
        conn.close();
    }
}
