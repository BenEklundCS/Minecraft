package com.beneklund.minecraft.net;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.ChunkPos;

/*
 * Everything that crosses between the Game client and server.
 */
public sealed interface IPacket {
    sealed interface ToServer extends IPacket {
        record PlayerInput(long tick, float moveX, float moveZ, boolean jump, boolean sneak, float pitch, float yaw)
                implements ToServer {}

        record BlockEdit(long tick, int x, int y, int z, Block block, boolean breaking) implements ToServer {}

        record Disconnect(String reason) implements ToServer {}
    }

    sealed interface ToClient extends IPacket {
        record ChunkData(ChunkPos pos, byte[] blocks) implements ToClient {}

        record ChunkUnload(ChunkPos pos) implements ToClient {}

        record BlockChanged(int x, int y, int z, Block block) implements ToClient {}

        record Chat(String message) implements ToClient {}

        record PlayerUpdate(long ackTick, float x, float y, float z, boolean onGround) implements ToClient {}

        record PlayerDisconnected(int playerId) implements ToClient {}

        record PlayerConnected(int playerId) implements ToClient {}
    }

    final class Join {
        record Request(String username, int protocolVersion) implements ToServer {}

        record Accepted(int playerId, long seed, long serverTick) implements ToClient {}

        record Rejected(String reason) implements ToClient {}

        private Join() {}
    }
}
