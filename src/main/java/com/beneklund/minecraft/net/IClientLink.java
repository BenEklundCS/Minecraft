package com.beneklund.minecraft.net;

import java.util.List;

// The servers handle to ONE connected client.
public interface IClientLink {
    int playerId();

    void send(IPacket.ToClient packet);

    List<IPacket.ToServer> drain();

    boolean isOpen();

    void close();
}
