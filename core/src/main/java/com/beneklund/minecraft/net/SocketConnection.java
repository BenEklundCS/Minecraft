package com.beneklund.minecraft.net;

import static com.beneklund.minecraft.util.Log.IO;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One connected {@link Socket}, a reader thread, a writer thread and two queues. The two thin
 * link adapters give it the {@link IServerLink} and {@link IClientLink} faces, the same split as
 * {@link InJvmLink}'s ServerEnd and ClientEnd.
 *
 * <p>The threads only move bytes into packets and packets into queues. They never call into
 * World, PlayerSession or Game. Callers see two non-blocking calls, {@link #send} and {@link
 * #drain}, so a slow peer can stall neither the {@code server-tick} thread nor the client's frame.
 *
 * <p>On the wire each packet is a 4-byte big-endian length followed by the body {@link
 * PacketCodec} produces. The reader closes the connection on a bad length, an undecodable body or
 * a packet of the wrong direction, so a corrupt stream never leaves it guessing at a boundary.
 *
 * <p>Closing either end closes both, and packets queued before {@link #close} are still written
 * before the socket shuts, which is the promise {@link InJvmLink} makes. Packets that arrived
 * before the close can still be drained.
 *
 * @param <In> what this end receives
 * @param <Out> what this end sends
 */
final class SocketConnection<In extends IPacket, Out extends IPacket> {
    private Socket socket;
    private PacketCodec codec;

    /** {@code ToServer.class} or {@code ToClient.class}; a packet of any other type closes the link. */
    private Class<In> inType;

    /** Filled by the reader thread, emptied by the owner through {@link #drain}. */
    private ConcurrentLinkedQueue<In> inbound;

    /** {@code Out} packets for the writer, plus {@link #CLOSE} to stop it. */
    private LinkedBlockingQueue<Object> outbound;

    private volatile boolean open = true;

    /**
     * Queued after the last packet to be written. The writer is parked in {@code take()}, so only
     * an item on the queue wakes it; going through the queue also keeps ordering, so everything
     * sent before the close goes out first.
     */
    private static final Object CLOSE = new Object();

    /**
     * Takes over a connected socket and starts one reader and one writer thread, both daemons.
     *
     * @param socket an already-connected socket; this connection owns it from here on
     * @param codec turns packets into frame bodies and back
     * @param inType the direction this end receives, to reject packets from the wrong side
     * @param name appended to the thread names, {@code net-read-<name>} and {@code net-write-<name>}
     * @throws SocketException if Nagle's algorithm can't be switched off
     */
    SocketConnection(Socket socket, PacketCodec codec, Class<In> inType, String name) throws SocketException {
        this.socket = socket;
        this.codec = codec;
        this.inType = inType;
        inbound = new ConcurrentLinkedQueue<>();
        outbound = new LinkedBlockingQueue<>();
        socket.setTcpNoDelay(true);
        startDaemon(this::readLoop, String.format("net-read-%s", name));
        startDaemon(this::writeLoop, String.format("net-write-%s", name));
    }

    /** Daemon, so a connection that outlives its owner can't keep the JVM from exiting. */
    private static void startDaemon(Runnable body, String threadName) {
        Thread thread = new Thread(body, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Queues a packet for the writer thread and returns at once. Dropped silently once closed.
     * Nothing bounds the queue, so a peer that stops reading makes it grow.
     */
    void send(Out packet) {
        if (open) {
            outbound.offer(packet);
        }
    }

    /** Every packet received since the last call, in arrival order; empty when there are none. */
    List<In> drain() {
        ArrayList<In> res = new ArrayList<>();
        In item;
        while ((item = inbound.poll()) != null) {
            res.add(item);
        }
        return res;
    }

    /**
     * False once either end has closed or the stream failed. When it turns false the reader has
     * finished or is about to, so a {@link #drain} after seeing it sees every packet that will
     * ever arrive.
     */
    boolean isOpen() {
        return open;
    }

    /**
     * Stops accepting sends and lets the writer flush what is already queued, then close the
     * socket. Safe to call more than once. Does not touch the socket itself.
     */
    void close() {
        open = false;
        outbound.offer(CLOSE);
    }

    /** Thread body of {@code net-read-<name>}: frames in, packets onto {@link #inbound}. */
    private void readLoop() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            while (true) {
                int len = in.readInt(); // EOFException: peer closed cleanly
                if (len <= 0 || len > PacketCodec.MAX_FRAME) throw new IOException("frame " + len);
                byte[] body = new byte[len];
                in.readFully(body);
                IPacket p = codec.decode(body);
                if (!inType.isInstance(p)) throw new IOException("wrong direction: " + p);
                inbound.add(inType.cast(p));
            }
        } catch (IOException e) {
            /* log at debug unless open was still true */
        } finally {
            open = false;
            outbound.offer(CLOSE);
        } // wakes the writer so it exits
    }

    /**
     * Thread body of {@code net-write-<name>}: packets off {@link #outbound}, frames out. Flushes
     * once per burst rather than per packet, and owns the socket's one {@code close()}, which also
     * unblocks a reader parked in {@code readInt}.
     */
    private void writeLoop() {
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            while (true) {
                Object next = outbound.take();
                if (next == CLOSE) {
                    out.flush();
                    return;
                }
                byte[] body = codec.encode((IPacket) next);
                out.writeInt(body.length);
                out.write(body);
                if (outbound.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            IO.debug("net-write ended: {}", e.toString());
        } finally {
            // The only socket.close() in the class: it also wakes a reader blocked in readInt.
            open = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closing
            }
        }
    }
}
