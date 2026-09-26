package com.beneklund.minecraft.net;

/** The authoritative simulation, advanced one tick at a time by its owner. */
public interface IGameServer {
    /** Adds a connection. The player joins once its {@link IPacket.Join.Request} is processed. */
    void accept(IClientLink client);

    void tick();

    /** Ticks completed since the server started. */
    long currentTick();
}
