package com.beneklund.minecraft.net;

import com.beneklund.minecraft.infra.ServerChunkManager;
import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import com.beneklund.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameServer implements IGameServer {
    private final World world;
    private final IWorldAuthority authority;
    private final ServerChunkManager chunks;
    private final IChunkStreamer streamer;
    private final IPlayerStore playerStore;
    private final PlayerState spawn;
    private final long seed;
    private long tick = 0;
    private final List<PlayerSession> players = new ArrayList<>();
    // Every edit applied this tick. Filled by apply, sent to every joined player during flush (not
    // just the one who made the edit), then cleared.
    private final List<IPacket.ToClient.BlockChanged> changesThisTick = new ArrayList<>();

    public GameServer(
            World world,
            IWorldAuthority authority,
            ServerChunkManager chunks,
            IChunkStreamer streamer,
            IPlayerStore playerStore,
            PlayerState spawn,
            long seed) {
        this.world = world;
        this.authority = authority;
        this.chunks = chunks;
        this.streamer = streamer;
        this.playerStore = playerStore;
        this.spawn = spawn;
        this.seed = seed;
    }

    @Override
    public void accept(IClientLink client) {
        players.add(new PlayerSession(client.playerId(), client));
    }

    public void tick() {
        players.forEach(PlayerSession::drainLink);
        players.forEach(this::apply);
        players.removeIf(session -> !session.isOpen());
        ChunkPos center = loadCenter();
        if (center != null) chunks.tick(center);
        players.forEach(session -> {
            stream(session);
            session.flush(tick);
        });
        changesThisTick.clear();
        tick++;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    // Players who left were saved as they went.
    public void saveConnectedPlayers() {
        players.forEach(this::save);
    }

    private void apply(PlayerSession session) {
        for (IPacket.ToServer packet : session.takeReceived()) {
            switch (packet) {
                case IPacket.Join.Request request -> join(session);
                case IPacket.ToServer.BlockEdit edit -> applyEdit(session, edit);
                case IPacket.ToServer.PlayerInput input -> session.consumedInput(input.tick());
                case IPacket.ToServer.PlayerPosition p ->
                    session.moved(new PlayerState(p.x(), p.y(), p.z(), p.pitch(), p.yaw()));
                case IPacket.ToServer.Disconnect disconnect -> {
                    save(session);
                    session.close();
                }
            }
        }
    }

    // Placed at the spawn, so chunks start loading there before the client reports anything.
    private void join(PlayerSession session) {
        session.markJoined();
        session.moved(spawn);
        session.queue(new IPacket.Join.Accepted(session.playerId(), seed, tick, spawn));
    }

    private void save(PlayerSession session) {
        if (session.isJoined()) playerStore.save(session.state());
    }

    // One player for now: the first joined one.
    private ChunkPos loadCenter() {
        for (PlayerSession session : players) {
            if (session.isJoined()) return session.chunkPos();
        }
        return null;
    }

    // "If the server agrees": joined, inside the world's height, and in a chunk this player was sent
    // and the server still holds. authority.setBlock returns silently on a bad target, so without
    // this a refused edit would still go out as a BlockChanged.
    private void applyEdit(PlayerSession session, IPacket.ToServer.BlockEdit edit) {
        if (!session.isJoined() || !Chunk.inYRange(edit.y())) return;
        ChunkPos pos = ChunkPos.containing(edit.x(), edit.z());
        if (!session.hasChunk(pos) || !chunks.replicable(pos)) return;
        authority.setBlock(edit.x(), edit.y(), edit.z(), edit.block());
        changesThisTick.add(new IPacket.ToClient.BlockChanged(edit.x(), edit.y(), edit.z(), edit.block()));
    }

    // Unloads, then chunks, then edits, so an edit never arrives for a chunk the client lacks.
    // replicable() before markChunkSent: a generating chunk is still air, and marking it would stick.
    private void stream(PlayerSession session) {
        if (!session.isJoined()) return;
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
        for (IPacket.ToClient.BlockChanged change : changesThisTick) {
            if (session.hasChunk(ChunkPos.containing(change.x(), change.z()))) session.queue(change);
        }
    }
}
