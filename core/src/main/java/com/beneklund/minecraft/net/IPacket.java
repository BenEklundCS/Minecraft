package com.beneklund.minecraft.net;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.chunk.ChunkPos;

/**
 * Every message that crosses between client and server, as a sealed hierarchy of records.
 *
 * <p>Direction is in the type: {@link ToServer} goes up, {@link ToClient} comes down, and the join
 * handshake in {@link Join} has one of each. The server's switch over {@link ToServer} is
 * exhaustive, so a new upward packet fails to compile until the server handles it; the client's
 * switch has a default and ignores packets it doesn't know.
 *
 * <p>Packets are plain values passed by reference over an {@link InJvmLink}; there is no wire
 * format yet. Arrays inside them, such as {@link ToClient.ChunkData#blocks()}, belong to the
 * receiver once sent.
 */
public sealed interface IPacket {
    /** Client to server. */
    sealed interface ToServer extends IPacket {
        record PlayerInput(long tick, PlayerIntent intent) implements ToServer {}

        /**
         * A break or place request. The client changes nothing locally; its replica updates when
         * the server answers with {@link ToClient.BlockChanged}.
         */
        record BlockEdit(long tick, int x, int y, int z, Block block, boolean breaking) implements ToServer {}

        record Disconnect(String reason) implements ToServer {}
    }

    /** Server to client. */
    sealed interface ToClient extends IPacket {
        /** One whole chunk's block ids, sent the first time the chunk is streamed to this player. */
        record ChunkData(ChunkPos pos, byte[] blocks) implements ToClient {}

        record ChunkUnload(ChunkPos pos) implements ToClient {}

        /** An edit the server applied, sent to every joined player. */
        record BlockChanged(int x, int y, int z, Block block) implements ToClient {}

        record Chat(String message) implements ToClient {}

        record PlayerUpdate(long ackTick, float x, float y, float z, float vy, boolean onGround) implements ToClient {}

        record PlayerDisconnected(int playerId) implements ToClient {}

        record PlayerConnected(int playerId) implements ToClient {}
    }

    /**
     * The handshake: the client sends {@link Request}, the server answers with {@link Accepted} or
     * {@link Rejected}.
     */
    // public: class members default to package-private, unlike interface members.
    final class Join {
        public record Request(String username, int protocolVersion) implements ToServer {}

        public record Accepted(int playerId, long seed, long serverTick, PlayerState spawn) implements ToClient {}

        public record Rejected(String reason) implements ToClient {}

        private Join() {}
    }
}
