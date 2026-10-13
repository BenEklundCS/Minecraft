package com.beneklund.minecraft.net;

import java.net.Socket;
import java.net.SocketException;
import java.util.List;

/**
 * The server-facing adapter for one socket connection.
 *
 * <p>The acceptor assigns this link's player ID. A {@link SocketConnection} handles socket I/O on
 * daemon threads; the game server calls {@link #drain()} and {@link #send(IPacket.ToClient)} from
 * its tick thread. Closing the link stops the connection while preserving packets already queued
 * for sending and packets already received for draining.
 */
public class SocketClientLink implements IClientLink {
    private static final String name = "client-%d";
    private final SocketConnection<IPacket.ToServer, IPacket.ToClient> conn;
    private final int playerId;

    /** Creates the server-side link for the player assigned {@code playerId}. */
    public SocketClientLink(Socket socket, PacketCodec codec, int playerId) throws SocketException {
        this.playerId = playerId;
        conn = new SocketConnection<>(socket, codec, IPacket.ToServer.class, String.format(name, playerId));
    }

    /** Returns the player ID assigned to this connection by the acceptor. */
    @Override
    public int playerId() {
        return playerId;
    }

    /** Queues a packet for the connected client; returns without waiting for socket I/O. */
    @Override
    public void send(IPacket.ToClient packet) {
        conn.send(packet);
    }

    /** Returns packets received from the client since the previous call, in arrival order. */
    @Override
    public List<IPacket.ToServer> drain() {
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
