package com.beneklund.minecraft.player;

import java.util.Optional;

public interface IPlayerStore {
    void save(PlayerState state);

    Optional<PlayerState> load();
}
