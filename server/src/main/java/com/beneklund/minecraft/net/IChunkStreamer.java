package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.ChunkPos;

/**
 * Decides which chunks a player may have. A client sees only what it was sent, so this is the
 * server's control over what each player can see.
 */
public interface IChunkStreamer {
    /** Whether a player at {@code player} should hold the chunk at {@code pos}. */
    boolean allowed(PlayerState player, ChunkPos pos);
}
