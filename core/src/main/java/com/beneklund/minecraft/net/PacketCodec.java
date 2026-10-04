package com.beneklund.minecraft.net;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes one packet tag and body. The connection owns outer length framing; this class owns the
 * stable tag-to-packet mapping and delegates each body to the codec for that packet type.
 */
public final class PacketCodec {
    /**
     * Bump on any body layout change. The Join.Request body (username, protocolVersion) is frozen
     * across versions so an old client's handshake always decodes and can be rejected with a reason.
     */
    public static final int PROTOCOL_VERSION = 1;
    /** Oldest client version this build still serves. Raise it when an old body layout is dropped. */
    public static final int MIN_COMPATIBLE_VERSION = 1;
    /** Largest frame body a reader will allocate. A ChunkData is 65,549; anything past this is garbage. */
    public static final int MAX_FRAME = 1 << 20;
    /** Longest reason a Join.Rejected or Disconnect carries; the receiving side shows it to the player. */
    static final int MAX_REASON_LENGTH = 256;
    // one tag per record, written as a byte. Explicit numbers, never ordinal(): the wire outlives the enum order.
    static final byte JOIN_REQUEST = 1,
            JOIN_ACCEPTED = 2,
            JOIN_REJECTED = 3,
            PLAYER_INPUT = 10,
            BLOCK_EDIT = 11,
            DISCONNECT = 12,
            CHUNK_DATA = 20,
            CHUNK_UNLOAD = 21,
            BLOCK_CHANGED = 22,
            CHAT = 23,
            PLAYER_UPDATE = 24,
            PLAYER_DISCONNECTED = 25,
            PLAYER_CONNECTED = 26;

    private final CodecRegistry codecs;
    /** Takes the registry that decides which block ids are valid on this side of the link. */
    public PacketCodec(BlockRegistry blocks) {
        codecs = new CodecRegistry(
                new JoinRequestCodec(),
                new JoinAcceptedCodec(),
                new JoinRejectedCodec(),
                new PlayerInputCodec(),
                new BlockEditCodec(blocks),
                new DisconnectCodec(),
                new ChunkDataCodec(),
                new ChunkUnloadCodec(),
                new BlockChangedCodec(blocks),
                new ChatCodec(),
                new PlayerUpdateCodec(),
                new PlayerDisconnectedCodec(),
                new PlayerConnectedCodec());
    }

    /**
     * Describes one packet's dispatch identity and body format. {@link CodecRegistry} consumes or
     * writes the tag first, so implementations handle only the packet fields that follow it.
     */
    abstract static class Codec {
        abstract byte tag();

        abstract Class<? extends IPacket> type();

        abstract void write(DataOutputStream out, IPacket packet) throws IOException;

        abstract IPacket read(DataInputStream in) throws IOException;
    }

    /**
     * Owns tag parsing and packet-type dispatch. Reads consume exactly one tag before delegation;
     * writes select by exact record class and emit its tag before the body codec runs.
     */
    static final class CodecRegistry {
        private final Map<Byte, Codec> byTag = new HashMap<>();
        private final Map<Class<?>, Codec> byType = new HashMap<>();

        CodecRegistry(Codec... codecs) {
            Arrays.stream(codecs).forEach(codec -> {
                if (byTag.put(codec.tag(), codec) != null) {
                    throw new IllegalArgumentException(String.format("Duplicate packet tag: %d", codec.tag()));
                }
                if (byType.put(codec.type(), codec) != null) {
                    throw new IllegalArgumentException(String.format(
                            "Duplicate packet type: %s", codec.type().getName()));
                }
            });
        }

        Codec forTag(byte tag) throws IOException {
            Codec codec = byTag.get(tag);

            if (codec == null) {
                throw malformed("Unknown packet tag: %d", tag);
            }

            return codec;
        }

        Codec forPacket(IPacket packet) throws IOException {
            Codec codec = byType.get(packet.getClass());

            if (codec == null) {
                throw malformed(
                        "Unregistered packet type: %s", packet.getClass().getName());
            }

            return codec;
        }

        void write(DataOutputStream out, IPacket packet) throws IOException {
            Codec codec = forPacket(packet);

            out.writeByte(codec.tag());
            codec.write(out, packet);
        }

        IPacket read(DataInputStream in) throws IOException {
            byte tag = in.readByte();

            Codec codec = forTag(tag);
            return codec.read(in);
        }
    }

    /** Body order: username (modified UTF, 2-byte big-endian byte length), then protocolVersion (big-endian int). */
    static final class JoinRequestCodec extends Codec {
        static final int MIN_USERNAME_LENGTH = 1;
        static final int MAX_USERNAME_LENGTH = 16;

        @Override
        byte tag() {
            return JOIN_REQUEST;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.Join.Request.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.Join.Request request = (IPacket.Join.Request) packet;

            writeText(out, request.username(), "Username", MIN_USERNAME_LENGTH, MAX_USERNAME_LENGTH);
            out.writeInt(request.protocolVersion());
        }

        @Override
        IPacket.Join.Request read(DataInputStream in) throws IOException {
            String username = readText(in, "Username", MIN_USERNAME_LENGTH, MAX_USERNAME_LENGTH);
            // Not checked here: the server answers a mismatch with Join.Rejected, which needs the request decoded.
            int protocolVersion = in.readInt();

            return new IPacket.Join.Request(username, protocolVersion);
        }
    }

    /** Body order, big-endian: playerId (int), seed (long), serverTick (long), spawn x/y/z/pitch/yaw (5 floats). */
    static final class JoinAcceptedCodec extends Codec {
        @Override
        byte tag() {
            return JOIN_ACCEPTED;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.Join.Accepted.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.Join.Accepted accepted = (IPacket.Join.Accepted) packet;
            out.writeInt(accepted.playerId());
            out.writeLong(accepted.seed());
            out.writeLong(accepted.serverTick());
            PlayerState state = accepted.spawn();
            out.writeFloat(state.x());
            out.writeFloat(state.y());
            out.writeFloat(state.z());
            out.writeFloat(state.pitch());
            out.writeFloat(state.yaw());
        }

        @Override
        IPacket.Join.Accepted read(DataInputStream in) throws IOException {
            int playerId = in.readInt();
            requireNonNegative(playerId, "Player id");
            long seed = in.readLong();
            long serverTick = in.readLong();
            requireNonNegative(serverTick, "Server tick");
            float x = readFiniteFloat(in);
            float y = readFiniteFloat(in);
            float z = readFiniteFloat(in);
            float pitch = readFiniteFloat(in);
            float yaw = readFiniteFloat(in);
            PlayerState spawn = new PlayerState(x, y, z, pitch, yaw);
            return new IPacket.Join.Accepted(playerId, seed, serverTick, spawn);
        }
    }

    /** Body order: reason (modified UTF, 2-byte big-endian encoded-length prefix). */
    static final class JoinRejectedCodec extends Codec {
        @Override
        byte tag() {
            return JOIN_REJECTED;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.Join.Rejected.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.Join.Rejected rejected = (IPacket.Join.Rejected) packet;
            writeText(out, rejected.reason(), "Reject reason", 0, MAX_REASON_LENGTH);
        }

        @Override
        IPacket.Join.Rejected read(DataInputStream in) throws IOException {
            String reason = readText(in, "Reject reason", 0, MAX_REASON_LENGTH);
            return new IPacket.Join.Rejected(reason);
        }
    }

    /** Body order, big-endian: tick (long), moveX/moveZ (floats), jump/sneak/flying (booleans), pitch/yaw (floats). */
    static final class PlayerInputCodec extends Codec {
        @Override
        byte tag() {
            return PLAYER_INPUT;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToServer.PlayerInput.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToServer.PlayerInput input = (IPacket.ToServer.PlayerInput) packet;
            out.writeLong(input.tick());
            PlayerIntent intent = input.intent();
            out.writeFloat(intent.moveX());
            out.writeFloat(intent.moveZ());
            out.writeBoolean(intent.jump());
            out.writeBoolean(intent.sneak());
            out.writeBoolean(intent.flying());
            out.writeFloat(intent.pitch());
            out.writeFloat(intent.yaw());
        }

        @Override
        IPacket.ToServer.PlayerInput read(DataInputStream in) throws IOException {
            long tick = in.readLong();
            requireNonNegative(tick, "Tick");
            float moveX = readFiniteFloat(in);
            float moveZ = readFiniteFloat(in);
            boolean jump = in.readBoolean();
            boolean sneak = in.readBoolean();
            boolean flying = in.readBoolean();
            float pitch = readFiniteFloat(in);
            float yaw = readFiniteFloat(in);
            PlayerIntent intent = new PlayerIntent(moveX, moveZ, jump, sneak, flying, pitch, yaw);
            return new IPacket.ToServer.PlayerInput(tick, intent);
        }
    }

    /** Body order, big-endian: tick (long), x/y/z (ints), block id (byte), breaking (boolean). */
    static final class BlockEditCodec extends Codec {
        private final BlockRegistry blocks;

        BlockEditCodec(BlockRegistry blocks) {
            this.blocks = blocks;
        }

        @Override
        byte tag() {
            return BLOCK_EDIT;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToServer.BlockEdit.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToServer.BlockEdit edit = (IPacket.ToServer.BlockEdit) packet;
            out.writeLong(edit.tick());
            out.writeInt(edit.x());
            out.writeInt(edit.y());
            out.writeInt(edit.z());
            out.writeByte(edit.block().id());
            out.writeBoolean(edit.breaking());
        }

        @Override
        IPacket.ToServer.BlockEdit read(DataInputStream in) throws IOException {
            long tick = in.readLong();
            requireNonNegative(tick, "Tick");
            int x = in.readInt();
            int y = readY(in);
            int z = in.readInt();
            Block block = readBlock(in, blocks);
            boolean breaking = in.readBoolean();
            return new IPacket.ToServer.BlockEdit(tick, x, y, z, block, breaking);
        }
    }

    /** Body order: reason (modified UTF, 2-byte big-endian encoded-length prefix). */
    static final class DisconnectCodec extends Codec {
        @Override
        byte tag() {
            return DISCONNECT;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToServer.Disconnect.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToServer.Disconnect disconnect = (IPacket.ToServer.Disconnect) packet;
            writeText(out, disconnect.reason(), "Disconnect reason", 0, MAX_REASON_LENGTH);
        }

        @Override
        IPacket.ToServer.Disconnect read(DataInputStream in) throws IOException {
            String reason = readText(in, "Disconnect reason", 0, MAX_REASON_LENGTH);
            return new IPacket.ToServer.Disconnect(reason);
        }
    }

    /** Body order, big-endian: chunk x/z (ints), block-byte length (int), then that many raw block-id bytes. */
    static final class ChunkDataCodec extends Codec {
        @Override
        byte tag() {
            return CHUNK_DATA;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.ChunkData.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.ChunkData data = (IPacket.ToClient.ChunkData) packet;
            ChunkPos pos = data.pos();
            out.writeInt(pos.x());
            out.writeInt(pos.z());
            byte[] blocks = data.blocks();
            if (blocks.length != Chunk.size()) {
                throw malformed("Chunk block length invalid: %d, expected %d", blocks.length, Chunk.size());
            }
            out.writeInt(blocks.length);
            out.write(blocks);
        }

        @Override
        IPacket.ToClient.ChunkData read(DataInputStream in) throws IOException {
            int x = in.readInt();
            int z = in.readInt();
            ChunkPos pos = new ChunkPos(x, z);

            int length = in.readInt();
            if (length != Chunk.size()) {
                throw malformed("Chunk block length invalid: %d, expected %d", length, Chunk.size());
            }

            byte[] blocks = in.readNBytes(length);
            if (blocks.length != length) {
                throw new EOFException(
                        String.format("Chunk block bytes truncated: expected %d, got %d", length, blocks.length));
            }

            return new IPacket.ToClient.ChunkData(pos, blocks);
        }
    }

    /** Body order, big-endian: chunk x (int), then chunk z (int). */
    static final class ChunkUnloadCodec extends Codec {
        @Override
        byte tag() {
            return CHUNK_UNLOAD;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.ChunkUnload.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.ChunkUnload unload = (IPacket.ToClient.ChunkUnload) packet;
            ChunkPos pos = unload.pos();
            out.writeInt(pos.x());
            out.writeInt(pos.z());
        }

        @Override
        IPacket.ToClient.ChunkUnload read(DataInputStream in) throws IOException {
            int x = in.readInt();
            int z = in.readInt();
            ChunkPos pos = new ChunkPos(x, z);
            return new IPacket.ToClient.ChunkUnload(pos);
        }
    }

    /** Body order, big-endian: x/y/z (ints), then block id (byte). */
    static final class BlockChangedCodec extends Codec {
        private final BlockRegistry blocks;

        BlockChangedCodec(BlockRegistry blocks) {
            this.blocks = blocks;
        }

        @Override
        byte tag() {
            return BLOCK_CHANGED;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.BlockChanged.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.BlockChanged changed = (IPacket.ToClient.BlockChanged) packet;
            out.writeInt(changed.x());
            out.writeInt(changed.y());
            out.writeInt(changed.z());
            out.writeByte(changed.block().id());
        }

        @Override
        IPacket.ToClient.BlockChanged read(DataInputStream in) throws IOException {
            int x = in.readInt();
            int y = readY(in);
            int z = in.readInt();
            Block block = readBlock(in, blocks);
            return new IPacket.ToClient.BlockChanged(x, y, z, block);
        }
    }

    /** Body order: message (modified UTF, 2-byte big-endian encoded-length prefix). */
    static final class ChatCodec extends Codec {
        static final int MIN_CHAT_LENGTH = 1;
        static final int MAX_CHAT_LENGTH = 256;

        @Override
        byte tag() {
            return CHAT;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.Chat.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.Chat chat = (IPacket.ToClient.Chat) packet;
            writeText(out, chat.message(), "Chat message", MIN_CHAT_LENGTH, MAX_CHAT_LENGTH);
        }

        @Override
        IPacket.ToClient.Chat read(DataInputStream in) throws IOException {
            String message = readText(in, "Chat message", MIN_CHAT_LENGTH, MAX_CHAT_LENGTH);
            return new IPacket.ToClient.Chat(message);
        }
    }

    /** Body order, big-endian: ackTick (long), x/y/z/vy (floats), onGround (boolean). */
    static final class PlayerUpdateCodec extends Codec {
        @Override
        byte tag() {
            return PLAYER_UPDATE;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.PlayerUpdate.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.PlayerUpdate update = (IPacket.ToClient.PlayerUpdate) packet;
            out.writeLong(update.ackTick());
            out.writeFloat(update.x());
            out.writeFloat(update.y());
            out.writeFloat(update.z());
            out.writeFloat(update.vy());
            out.writeBoolean(update.onGround());
        }

        @Override
        IPacket.ToClient.PlayerUpdate read(DataInputStream in) throws IOException {
            long ackTick = in.readLong();
            requireNonNegative(ackTick, "Ack tick");
            float x = readFiniteFloat(in);
            float y = readFiniteFloat(in);
            float z = readFiniteFloat(in);
            float vy = readFiniteFloat(in);
            boolean onGround = in.readBoolean();
            return new IPacket.ToClient.PlayerUpdate(ackTick, x, y, z, vy, onGround);
        }
    }

    /** Body order, big-endian: playerId (int). */
    static final class PlayerDisconnectedCodec extends Codec {
        @Override
        byte tag() {
            return PLAYER_DISCONNECTED;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.PlayerDisconnected.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.PlayerDisconnected disconnected = (IPacket.ToClient.PlayerDisconnected) packet;
            out.writeInt(disconnected.playerId());
        }

        @Override
        IPacket.ToClient.PlayerDisconnected read(DataInputStream in) throws IOException {
            int playerId = in.readInt();
            requireNonNegative(playerId, "Player id");
            return new IPacket.ToClient.PlayerDisconnected(playerId);
        }
    }

    /** Body order, big-endian: playerId (int). */
    static final class PlayerConnectedCodec extends Codec {
        @Override
        byte tag() {
            return PLAYER_CONNECTED;
        }

        @Override
        Class<? extends IPacket> type() {
            return IPacket.ToClient.PlayerConnected.class;
        }

        @Override
        void write(DataOutputStream out, IPacket packet) throws IOException {
            IPacket.ToClient.PlayerConnected connected = (IPacket.ToClient.PlayerConnected) packet;
            out.writeInt(connected.playerId());
        }

        @Override
        IPacket.ToClient.PlayerConnected read(DataInputStream in) throws IOException {
            int playerId = in.readInt();
            requireNonNegative(playerId, "Player id");
            return new IPacket.ToClient.PlayerConnected(playerId);
        }
    }

    /** A newer client is never compatible: this build can't know what it changed. */
    public static boolean isCompatible(int clientVersion) {
        return clientVersion >= MIN_COMPATIBLE_VERSION && clientVersion <= PROTOCOL_VERSION;
    }

    /** Tag then fields, no length prefix; the connection writes the length. */
    public void write(DataOutputStream out, IPacket packet) throws IOException {
        codecs.write(out, packet);
    }

    /** Reads exactly one body. Throws on an unknown tag, so a bad stream closes the link instead of guessing. */
    public IPacket read(DataInputStream in) throws IOException {
        return codecs.read(in);
    }

    // convenience for tests and for the connection's framing
    public byte[] encode(IPacket packet) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            write(out, packet);
        }

        return bytes.toByteArray();
    }

    public IPacket decode(byte[] body) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            IPacket packet = read(in);
            if (in.available() > 0) {
                throw malformed("Trailing bytes after packet: %d", in.available());
            }
            return packet;
        }
    }

    /** Whether a codec is registered for {@code type}; a test uses it to catch a packet added without one. */
    boolean handles(Class<?> type) {
        return codecs.byType.containsKey(type);
    }

    // Every read failure goes through here so messages share one shape: "<Subject> <problem>: <value>".
    private static IOException malformed(String format, Object... args) {
        return new IOException(String.format(format, args));
    }

    private static void requireNonNegative(long value, String what) throws IOException {
        if (value < 0) {
            throw malformed("%s negative: %d", what, value);
        }
    }

    private static String readText(DataInputStream in, String what, int min, int max) throws IOException {
        return checkText(in.readUTF(), what, min, max);
    }

    private static void writeText(DataOutputStream out, String text, String what, int min, int max) throws IOException {
        out.writeUTF(checkText(text, what, min, max));
    }

    // Length is in UTF-16 units, the same count String.length() gives the rest of the game.
    private static String checkText(String text, String what, int min, int max) throws IOException {
        if (text.length() < min || text.length() > max) {
            throw malformed("%s length out of range: %d, expected %d to %d", what, text.length(), min, max);
        }
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw malformed("%s contains control characters", what);
        }
        return text;
    }

    private static float readFiniteFloat(DataInputStream in) throws IOException {
        float f = in.readFloat();
        if (!Float.isFinite(f)) {
            throw malformed("Non-finite float: %s", f);
        }
        return f;
    }

    private static int readY(DataInputStream in) throws IOException {
        int y = in.readInt();
        if (y < 0 || y >= Chunk.SIZE_Y) {
            throw malformed("Block y out of range: %d, expected 0 to %d", y, Chunk.SIZE_Y - 1);
        }
        return y;
    }

    private static Block readBlock(DataInputStream in, BlockRegistry blocks) throws IOException {
        byte id = in.readByte();
        return blocks.byId(id).orElseThrow(() -> malformed("Unknown block id: %d", id));
    }
}
