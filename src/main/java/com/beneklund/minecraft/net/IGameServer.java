package com.beneklund.minecraft.net;

public interface IGameServer {
    void accept(IClientLink client);

    void tick();

    long currentTick();
}
