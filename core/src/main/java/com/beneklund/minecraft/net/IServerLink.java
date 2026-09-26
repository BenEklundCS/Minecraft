package com.beneklund.minecraft.net;

import java.util.List;

/**
 * The client's end of its connection to the server. One per client process.
 *
 * <p>Sends are fire-and-forget and {@link #drain()} returns whatever has arrived since the last
 * call, oldest first, so the client polls once per frame on the main thread.
 */
public interface IServerLink {
    void send(IPacket.ToServer packet);

    /** Every packet received since the last call, in arrival order; empty when there are none. */
    List<IPacket.ToClient> drain();

    boolean isOpen();

    void close();
}
