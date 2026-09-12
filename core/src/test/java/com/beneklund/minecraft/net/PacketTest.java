package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.ChunkPos;
import org.junit.jupiter.api.Test;

class PacketTest {
    private static String describe(IPacket.ToClient packet) {
        return switch (packet) {
            case IPacket.Join.Accepted p -> "join " + p.playerId();
            case IPacket.Join.Rejected p -> "rejected " + p.reason();
            case IPacket.ToClient.ChunkData p -> "chunk " + p.pos();
            case IPacket.ToClient.ChunkUnload p -> "unload " + p.pos();
            case IPacket.ToClient.BlockChanged p -> "block " + p.block();
            case IPacket.ToClient.Chat p -> "chat " + p.message();
            case IPacket.ToClient.PlayerUpdate p -> "state @" + p.ackTick();
            case IPacket.ToClient.PlayerConnected p -> "connected " + p.playerId();
            case IPacket.ToClient.PlayerDisconnected p -> "disconnected " + p.playerId();
        };
    }

    private static String describe(IPacket.ToServer packet) {
        return switch (packet) {
            case IPacket.Join.Request p -> "join " + p.username();
            case IPacket.ToServer.PlayerInput p -> "input @" + p.tick();
            case IPacket.ToServer.BlockEdit p -> "edit @" + p.tick();
            case IPacket.ToServer.Disconnect p -> "bye " + p.reason();
        };
    }

    // Every variant gets an assertion, not because the values are interesting, but because a
    // switch arm with nothing routed through it is an arm nobody has ever seen run.
    @Test
    void everyToClientPacketIsHandledWithoutADefaultBranch() {
        assertEquals("join 7", describe(new IPacket.Join.Accepted(7, 1234L, 0L)));
        assertEquals("rejected old client", describe(new IPacket.Join.Rejected("old client")));
        assertEquals(
                "chunk ChunkPos[x=1, z=2]", describe(new IPacket.ToClient.ChunkData(new ChunkPos(1, 2), new byte[0])));
        assertEquals("unload ChunkPos[x=3, z=4]", describe(new IPacket.ToClient.ChunkUnload(new ChunkPos(3, 4))));
        assertEquals("block STONE", describe(new IPacket.ToClient.BlockChanged(0, 64, 0, Block.STONE)));
        assertEquals("chat hello", describe(new IPacket.ToClient.Chat("hello")));
        assertEquals("state @412", describe(new IPacket.ToClient.PlayerUpdate(412L, 1f, 2f, 3f, true)));
        assertEquals("disconnected 9", describe(new IPacket.ToClient.PlayerDisconnected(9)));
        assertEquals("connected 10", describe(new IPacket.ToClient.PlayerConnected(10)));
    }

    @Test
    void everyToServerPacketIsHandledWithoutADefaultBranch() {
        assertEquals("join ben", describe(new IPacket.Join.Request("ben", 1)));
        assertEquals("input @7", describe(new IPacket.ToServer.PlayerInput(7L, 1f, 0f, false, false, 0f, 90f)));
        assertEquals("edit @9", describe(new IPacket.ToServer.BlockEdit(9L, 0, 64, 0, Block.STONE, true)));
        assertEquals("bye quit", describe(new IPacket.ToServer.Disconnect("quit")));
    }
}
