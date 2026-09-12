package com.beneklund.minecraft.net;

import java.util.List;

// The client's handle to the server. One per client process.
public interface IServerLink {
    void send(IPacket.ToServer packet);

    List<IPacket.ToClient> drain();

    boolean isOpen();

    void close();
}
