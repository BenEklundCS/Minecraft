package com.beneklund.minecraft.net;

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
    private final long seed;
    private long tick = 0;
    private final List<PlayerSession> players = new ArrayList<>();
    // Every edit applied this tick. Filled by apply, sent to every joined player during flush (not
    // just the one who made the edit), then cleared.
    private final List<IPacket.ToClient.BlockChanged> changesThisTick = new ArrayList<>();

    public GameServer(World world, IWorldAuthority authority, long seed) {
        this.world = world;
        this.authority = authority;
        this.seed = seed;
    }

    @Override
    public void accept(IClientLink client) {
        players.add(new PlayerSession(client.playerId(), client));
    }

    public void tick() {
        players.forEach(PlayerSession::drainLink);
        players.forEach(this::apply);
        players.forEach(s -> {
            queueOwed(s);
            s.flush(tick);
        });
        changesThisTick.clear();
        tick++;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    private void apply(PlayerSession session) {
        for (IPacket.ToServer packet : session.takeReceived()) {
            switch (packet) {
                case IPacket.Join.Request request -> join(session);
                case IPacket.ToServer.BlockEdit edit -> applyEdit(session, edit);
                case IPacket.ToServer.PlayerInput input -> session.consumedInput(input.tick());
                case IPacket.ToServer.Disconnect disconnect -> session.close();
            }
        }
    }

    private void join(PlayerSession session) {
        session.markJoined();
        session.queue(new IPacket.Join.Accepted(session.playerId(), seed, tick));
    }

    // "If the server agrees." For now that only means the player has joined and the target is inside
    // the world's height, in a chunk the server holds. authority.setBlock checks the last two
    // itself but returns silently, so without checking here a refused edit would still go out as a
    // BlockChanged. Reach and breakability checks belong here later.
    private void applyEdit(PlayerSession session, IPacket.ToServer.BlockEdit edit) {
        if (!session.isJoined() || !Chunk.inYRange(edit.y())) return;
        ChunkPos pos = new ChunkPos(Math.floorDiv(edit.x(), Chunk.SIZE_XZ), Math.floorDiv(edit.z(), Chunk.SIZE_XZ));
        if (authority.getChunk(pos) == null) return;
        authority.setBlock(edit.x(), edit.y(), edit.z(), edit.block());
        changesThisTick.add(new IPacket.ToClient.BlockChanged(edit.x(), edit.y(), edit.z(), edit.block()));
    }

    // What a joined player is owed on top of what apply queued: chunks it hasn't been sent, then
    // this tick's edits. Chunks go first so a client never hears about a block in a chunk it
    // doesn't have yet.
    //
    // serialize() is the copy the wire needs: a fresh byte[], never the server's Chunk. Every
    // chunk in the world counts as owed for now; a radius around the player comes with movement.
    // Once chunks come from ChunkManager this also has to skip chunks still generating, or an empty
    // chunk goes out, is marked sent, and is never sent again.
    private void queueOwed(PlayerSession session) {
        if (!session.isJoined()) return;
        for (Map.Entry<ChunkPos, Chunk> entry : world.getChunkEntries()) {
            if (session.markChunkSent(entry.getKey())) {
                session.queue(new IPacket.ToClient.ChunkData(
                        entry.getKey(), entry.getValue().serialize()));
            }
        }
        for (IPacket.ToClient.BlockChanged change : changesThisTick) session.queue(change);
    }
}
