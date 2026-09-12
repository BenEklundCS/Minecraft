package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.ChunkPos;

// Which chunks a player may have. A client can't see what it was never sent.
public interface IChunkStreamer {
    boolean allowed(PlayerState player, ChunkPos pos);
}
