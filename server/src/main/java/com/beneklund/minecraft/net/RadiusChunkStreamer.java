package com.beneklund.minecraft.net;

import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.ChunkPos;

// A square around the player's chunk, the same shape ServerChunkManager loads.
public class RadiusChunkStreamer implements IChunkStreamer {
    private final int radius;

    public RadiusChunkStreamer(int radius) {
        this.radius = radius;
    }

    @Override
    public boolean allowed(PlayerState player, ChunkPos pos) {
        ChunkPos center = ChunkPos.containing(player.x(), player.z());
        return Math.abs(pos.x() - center.x()) <= radius && Math.abs(pos.z() - center.z()) <= radius;
    }
}
