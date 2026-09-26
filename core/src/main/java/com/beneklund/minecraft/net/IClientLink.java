package com.beneklund.minecraft.net;

import java.util.List;

/**
 * The server's end of one client's connection, polled on the {@code server-tick} thread.
 *
 * <p>The mirror of {@link IServerLink}: fire-and-forget sends, and {@link #drain()} returns what
 * arrived since the last call, oldest first.
 */
public interface IClientLink {
    /** The id the server assigned this player, fixed for the connection's life. */
    int playerId();

    void send(IPacket.ToClient packet);

    List<IPacket.ToServer> drain();

    boolean isOpen();

    void close();
}
