package com.beneklund.minecraft.net;

import static com.beneklund.minecraft.net.RoundTripTest.ONLY_CHUNK;
import static com.beneklund.minecraft.net.RoundTripTest.SPAWN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.BlockRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The RoundTripTest fixture, reached over a real loopback socket instead of an InJvmLink. */
class SocketRoundTripTest {
    private static final long TIMEOUT_MS = 2000;

    @Test
    void joinOverLoopbackStreamsTheChunk() throws IOException, InterruptedException {
        RoundTripTest.Fixture fixture = RoundTripTest.serverWithOneChunk();
        PacketCodec codec = new PacketCodec(BlockRegistry.createDefault());

        SocketAcceptor acceptor = new SocketAcceptor(0, codec, fixture.server());
        acceptor.start();
        SocketServerLink link = null;
        try {
            link = new SocketServerLink(new Socket(InetAddress.getLoopbackAddress(), acceptor.port()), codec);
            link.send(new IPacket.ToServer.Join.Request("ben", PacketCodec.PROTOCOL_VERSION));

            // This thread stands in for server-tick. The accept and the bytes both land on their own
            // schedule, so tick and drain until the join has been answered rather than sleeping.
            List<IPacket.ToClient> received = new ArrayList<>();
            long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000;
            while (System.nanoTime() < deadline
                    && !(has(received, IPacket.Join.Accepted.class)
                            && has(received, IPacket.ToClient.ChunkData.class))) {
                fixture.server().tick();
                received.addAll(link.drain());
                Thread.sleep(1);
            }

            IPacket.Join.Accepted accepted = received.stream()
                    .filter(IPacket.Join.Accepted.class::isInstance)
                    .map(IPacket.Join.Accepted.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no Join.Accepted within " + TIMEOUT_MS + " ms"));
            assertEquals(SPAWN, accepted.spawn());

            IPacket.ToClient.ChunkData data = received.stream()
                    .filter(IPacket.ToClient.ChunkData.class::isInstance)
                    .map(IPacket.ToClient.ChunkData.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no ChunkData within " + TIMEOUT_MS + " ms"));
            assertEquals(ONLY_CHUNK, data.pos());
            assertArrayEquals(fixture.world().getChunk(ONLY_CHUNK).serialize(), data.blocks());
        } finally {
            if (link != null) link.close();
            acceptor.close();
        }
    }

    private static boolean has(List<IPacket.ToClient> packets, Class<? extends IPacket.ToClient> type) {
        return packets.stream().anyMatch(type::isInstance);
    }
}
