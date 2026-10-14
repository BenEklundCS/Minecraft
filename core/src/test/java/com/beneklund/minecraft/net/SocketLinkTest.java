package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Loopback fixture: ServerSocket(0), read the port back with getLocalPort(), connect a Socket to it
// on this thread and accept() the other end. Wrap as SocketServerLink (the client's side) and
// SocketClientLink(playerId 7) (the server's side).
//
// helpers: drainUntil(link, n, 2 s) and awaitClosed(link, 2 s) poll every ms and fail on timeout.
// The only waiting in the suite.
public class SocketLinkTest {

    // send PlayerInput 0..99 up; drainUntil 100; ticks are 0..99
    @Test
    void inputsArriveInOrder() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            client.send(new IPacket.ToServer.PlayerInput(i, playerIntent()));
        }
        List<IPacket.ToServer> inputs = drainUntil(server::drain, 100, 2000);
        for (int i = 0; i < 100; i++) {
            assertEquals(i, ((IPacket.ToServer.PlayerInput) inputs.get(i)).tick());
        }
    }

    // send one ChunkData down with a patterned 65,536-byte array; arrays equal.
    // ChunkData's generated equals compares the array by reference: compare pos() and
    // assertArrayEquals(blocks()).
    @Test
    void aChunkCrossesWhole() throws InterruptedException {
        var expect = chunkData();
        server.send(expect);
        List<IPacket.ToClient> chunks = drainUntil(client::drain, 1, 2000);
        IPacket.ToClient.ChunkData chunk = (IPacket.ToClient.ChunkData) chunks.getFirst();
        assertEquals(expect.pos(), chunk.pos());
        assertArrayEquals(expect.blocks(), chunk.blocks());
    }

    // server end: send Rejected, close(); client end drains exactly one Rejected, then isOpen() == false
    @Test
    void closeDeliversWhatWasQueued() throws InterruptedException {
        server.send(new IPacket.Join.Rejected("no"));
        server.close();
        List<IPacket.ToClient> packets = new ArrayList<>(drainUntil(client::drain, 1, 2000));
        awaitClosed(client, 2000);
        // closed means the reader has finished, so this sees everything that will ever arrive
        packets.addAll(client.drain());
        assertEquals(1, packets.size());
        assertEquals(new IPacket.Join.Rejected("no"), packets.getFirst());
    }

    // close the client end; within 2 s the server end reports !isOpen()
    @Test
    void peerCloseClosesThisEnd() throws InterruptedException {
        client.close();
        awaitClosed(server, 2000);
    }

    // close, send, nothing arrives and nothing throws
    @Test
    void sendAfterCloseIsDropped() throws InterruptedException {
        client.close();
        client.send(new IPacket.ToServer.Disconnect("bye"));
        // nothing is coming, so wait for the close instead: it happens after the send would have
        awaitClosed(server, 2000);
        assertTrue(server.drain().isEmpty());
    }

    // raw socket writes len=5, tag=99; the link closes and nothing is queued
    @Test
    void garbageClosesTheLink() throws IOException, InterruptedException {
        DataOutputStream raw = new DataOutputStream(clientSocket.getOutputStream());
        raw.writeInt(5); // the reader must find all 5 bytes, or it blocks in readFully
        raw.write(99); // unknown tag
        raw.write(new byte[4]);

        awaitClosed(server, 2000);
        assertTrue(server.drain().isEmpty());
    }

    private final PacketCodec codec = new PacketCodec(BlockRegistry.createDefault());

    private ServerSocket listener;
    private Socket clientSocket;
    private SocketConnection<IPacket.ToClient, IPacket.ToServer> client;
    private SocketConnection<IPacket.ToServer, IPacket.ToClient> server;

    @BeforeEach
    void setUp() throws IOException {
        listener = new ServerSocket(0);
        clientSocket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket acceptedSocket = listener.accept();
        client = new SocketConnection<>(clientSocket, codec, IPacket.ToClient.class, "client");
        server = new SocketConnection<>(acceptedSocket, codec, IPacket.ToServer.class, "server");
    }

    // The connections close their own sockets through the writer; the listener is ours.
    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.close();
        listener.close();
    }

    /** Polls drain() every ms until n packets have arrived, in arrival order. Fails after timeoutMillis. */
    /** Polls isOpen() every ms until it is false. Fails after timeoutMillis. */
    private static void awaitClosed(SocketConnection<?, ?> conn, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (conn.isOpen()) {
            if (System.nanoTime() > deadline) {
                fail("still open after " + timeoutMillis + " ms");
            }
            Thread.sleep(1);
        }
    }

    private static <T extends IPacket> List<T> drainUntil(Supplier<List<T>> drain, int n, long timeoutMillis)
            throws InterruptedException {
        List<T> received = new ArrayList<>();
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (received.size() < n) {
            if (System.nanoTime() > deadline) {
                fail("wanted " + n + " packets, got " + received.size() + " within " + timeoutMillis + " ms");
            }
            received.addAll(drain.get());
            Thread.sleep(1);
        }
        return received;
    }

    private static PlayerIntent playerIntent() {
        return new PlayerIntent(0.0f, 0.0f, false, false, false, 0.0f, 0.0f);
    }

    private IPacket.ToClient.ChunkData chunkData() {
        byte[] data = new byte[Chunk.size()];
        for (int i = 0; i < Chunk.size(); i++) {
            data[i] = Block.STONE.id();
        }
        return new IPacket.ToClient.ChunkData(new ChunkPos(1, 2), data);
    }
}
