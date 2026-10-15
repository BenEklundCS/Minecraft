package com.beneklund.minecraft.net.replicator;

import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.net.IChunkStreamer;
import com.beneklund.minecraft.net.IPacket;
import com.beneklund.minecraft.net.PlayerSession;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.util.List;
import java.util.Map;

/**
 * Keeps each player's chunk replica current: unloads first, then chunk data.
 *
 * <p>A chunk is sent once, the first tick it is both {@code LIVE} and allowed by the {@link
 * IChunkStreamer}. The {@code replicable} check comes before {@code markChunkSent} because a
 * generating chunk is still air, and marking it sent would stop the real one from ever going out.
 */
public class ChunkStreamReplicator implements IReplicator {
    private final World world;
    private final ServerChunkManager chunks;
    private final IChunkStreamer streamer;

    public ChunkStreamReplicator(World world, ServerChunkManager chunks, IChunkStreamer streamer) {
        this.world = world;
        this.chunks = chunks;
        this.streamer = streamer;
    }

    @Override
    public void replicate(long tick, List<PlayerSession> joined) {
        joined.forEach(this::stream);
    }

    private void stream(PlayerSession session) {
        PlayerState player = session.state();
        for (ChunkPos pos : session.sentChunks()) {
            if (streamer.allowed(player, pos) && world.hasChunk(pos)) continue;
            session.markChunkUnloaded(pos);
            session.queue(new IPacket.ToClient.ChunkUnload(pos));
        }
        for (Map.Entry<ChunkPos, Chunk> entry : world.getChunkEntries()) {
            ChunkPos pos = entry.getKey();
            if (!chunks.replicable(pos) || !streamer.allowed(player, pos)) continue;
            if (session.markChunkSent(pos)) {
                // serialize() is the copy: a fresh byte[], never the server's Chunk.
                session.queue(
                        new IPacket.ToClient.ChunkData(pos, entry.getValue().serialize()));
            }
        }
    }
}
