package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.player.PlayerIntent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// SocketLinkTest covers the connection underneath. This covers only what the two adapters add:
// each face forwards to its connection, the directions aren't crossed, and the player id is the
// one the acceptor handed in. The names cross: toServer is what the CLIENT holds, toClient is
// what the SERVER holds.
class SocketLinkAdapterTest {
    private static final int PLAYER_ID = 7;
    private static final long TIMEOUT_MS = 2000;

    @Test
    void playerIdIsTheOneGiven() {
        assertEquals(PLAYER_ID, toClient.playerId());
    }

    @Test
    void aPacketSentUpIsDrainedByTheServersEnd() throws InterruptedException {
        IPacket.ToServer.PlayerInput input =
                new IPacket.ToServer.PlayerInput(3, new PlayerIntent(1.0f, 0.0f, true, false, false, 0.0f, 90.0f));
        toServer.send(input);
        assertEquals(List.of(input), drainUntil(toClient::drain, 1));
    }

    @Test
    void aPacketSentDownIsDrainedByTheClientsEnd() throws InterruptedException {
        IPacket.Join.Rejected rejected = new IPacket.Join.Rejected("no");
        toClient.send(rejected);
        assertEquals(List.of(rejected), drainUntil(toServer::drain, 1));
    }

    @Test
    void bothEndsStartOpen() {
        assertTrue(toServer.isOpen());
        assertTrue(toClient.isOpen());
    }

    @Test
    void closingTheClientsEndClosesTheServers() throws InterruptedException {
        toServer.close();
        awaitClosed(toClient::isOpen);
    }

    @Test
    void closingTheServersEndClosesTheClients() throws InterruptedException {
        toClient.close();
        awaitClosed(toServer::isOpen);
    }

    private final PacketCodec codec = new PacketCodec(BlockRegistry.createDefault());

    private ServerSocket listener;
    private SocketServerLink toServer;
    private SocketClientLink toClient;

    @BeforeEach
    void setUp() throws IOException {
        listener = new ServerSocket(0);
        Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket acceptedSocket = listener.accept();
        toServer = new SocketServerLink(clientSocket, codec);
        toClient = new SocketClientLink(acceptedSocket, codec, PLAYER_ID);
    }

    // The links close their own sockets through the connection's writer; the listener is ours.
    @AfterEach
    void tearDown() throws IOException {
        toServer.close();
        toClient.close();
        listener.close();
    }

    private static void awaitClosed(BooleanSupplier isOpen) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000;
        while (isOpen.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("still open after " + TIMEOUT_MS + " ms");
            Thread.sleep(1);
        }
    }

    private static <T extends IPacket> List<T> drainUntil(Supplier<List<T>> drain, int n) throws InterruptedException {
        List<T> received = new ArrayList<>();
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000;
        while (received.size() < n) {
            if (System.nanoTime() > deadline) {
                fail("wanted " + n + " packets, got " + received.size() + " within " + TIMEOUT_MS + " ms");
            }
            received.addAll(drain.get());
            Thread.sleep(1);
        }
        return received;
    }
}
