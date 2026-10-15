package com.beneklund.minecraft.net;

import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.net.replicator.*;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerMovement;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.sky.DayNightCycle;
import java.util.List;

/** Builds a {@link GameServer} with the replicators the real container registers, in its order. */
final class TestServers {
    static final float TICK_SECONDS = 1f / 20;

    private TestServers() {}

    static GameServer create(
            World world,
            IWorldAuthority authority,
            ServerChunkManager chunks,
            IChunkStreamer streamer,
            IPlayerStore store,
            PlayerState spawn,
            PlayerMovement movement,
            long seed) {
        DayNightCycle cycle = new DayNightCycle(DayNightCycle.MORNING, DayNightCycle.DayLength.DEFAULT);
        BlockChangeReplicator edits = new BlockChangeReplicator();
        List<IReplicator> replicators = List.of(
                new ChunkStreamReplicator(world, chunks, streamer),
                edits,
                new PlayerStateReplicator(),
                new TimeReplicator(cycle, 20));
        return new GameServer(
                authority, chunks, store, spawn, movement, seed, cycle, TICK_SECONDS, edits::record, replicators);
    }
}
