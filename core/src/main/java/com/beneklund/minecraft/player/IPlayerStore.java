package com.beneklund.minecraft.player;

import java.util.Optional;

/** Persists the local player's position and orientation between sessions. */
public interface IPlayerStore {
    void save(PlayerState state);

    /** The saved state, or empty when there is no save or it fails validation. */
    Optional<PlayerState> load();
}
