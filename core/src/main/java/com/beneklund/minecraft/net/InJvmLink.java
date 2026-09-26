package com.beneklund.minecraft.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A client-server connection inside one JVM: two lock-free queues and a shared open flag.
 *
 * <p>Singleplayer runs the server on its own thread in the same process, and this link joins the
 * two. The client's main thread and the {@code server-tick} thread each send into one {@link
 * ConcurrentLinkedQueue} and drain the other, so neither blocks the other. Packets pass by
 * reference with no serialisation.
 *
 * <p>Closing either end closes both. A send after close is dropped; packets already queued can
 * still be drained.
 */
public final class InJvmLink {

    private InJvmLink() {}

    /**
     * Both ends of one connection. {@code server} is the client's handle to the server, {@code
     * client} is the server's handle to this client.
     */
    public record Pair(IServerLink server, IClientLink client) {}
    ;

    /** Opens a connection for the player the server will know as {@code playerId}. */
    public static Pair connect(int playerId) {
        Queue<IPacket.ToServer> upward = new ConcurrentLinkedQueue<>();
        Queue<IPacket.ToClient> downward = new ConcurrentLinkedQueue<>();
        Connection conn = new Connection();
        return new Pair(new ServerEnd(upward, downward, conn), new ClientEnd(playerId, upward, downward, conn));
    }

    private static final class Connection {
        private volatile boolean open = true;

        public void close() {
            open = false;
        }

        public boolean open() {
            return open;
        }
    }

    private static <T> List<T> drainInto(Queue<T> queue) {
        ArrayList<T> res = new ArrayList<>();
        T item;
        while ((item = queue.poll()) != null) {
            res.add(item);
        }
        return res;
    }

    private record ServerEnd(Queue<IPacket.ToServer> upward, Queue<IPacket.ToClient> downward, Connection conn)
            implements IServerLink {

        @Override
        public void send(IPacket.ToServer packet) {
            if (!isOpen()) return;
            upward.add(packet);
        }

        @Override
        public List<IPacket.ToClient> drain() {
            return drainInto(downward);
        }

        @Override
        public boolean isOpen() {
            return conn.open();
        }

        @Override
        public void close() {
            conn.close();
        }
    }

    private record ClientEnd(
            int playerId, Queue<IPacket.ToServer> upward, Queue<IPacket.ToClient> downward, Connection conn)
            implements IClientLink {

        @Override
        public void send(IPacket.ToClient packet) {
            if (!isOpen()) return;
            downward.add(packet);
        }

        @Override
        public List<IPacket.ToServer> drain() {
            return drainInto(upward);
        }

        @Override
        public boolean isOpen() {
            return conn.open();
        }

        @Override
        public void close() {
            conn.close();
        }
    }
}
