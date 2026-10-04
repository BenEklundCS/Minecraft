package com.beneklund.minecraft.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.player.PlayerIntent;
import com.beneklund.minecraft.player.PlayerState;
import com.beneklund.minecraft.world.chunk.Chunk;
import com.beneklund.minecraft.world.chunk.ChunkPos;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PacketCodecTest {
    private final PacketCodec codec = new PacketCodec(BlockRegistry.createDefault());

    // one example of every record; reuse PacketTest's values so the two stay in step
    @Test
    void everyPacketRoundTrips() throws IOException {
        assertEquals(playerInput(), codec.decode(codec.encode(playerInput())));
        assertEquals(blockEdit(), codec.decode(codec.encode(blockEdit())));
        assertEquals(disconnect(), codec.decode(codec.encode(disconnect())));
        assertEquals(chunkUnload(), codec.decode(codec.encode(chunkUnload())));
        assertEquals(blockChanged(), codec.decode(codec.encode(blockChanged())));
        assertEquals(chat(), codec.decode(codec.encode(chat())));
        assertEquals(
                playerUpdate(0.0f, 0.0f, 0.0f, 0.0f), codec.decode(codec.encode(playerUpdate(0.0f, 0.0f, 0.0f, 0.0f))));
        assertEquals(playerDisconnected(), codec.decode(codec.encode(playerDisconnected())));
        assertEquals(playerConnected(), codec.decode(codec.encode(playerConnected())));
        assertEquals(joinRequest(), codec.decode(codec.encode(joinRequest())));
        assertEquals(joinAccepted(), codec.decode(codec.encode(joinAccepted())));
        assertEquals(joinRejected(), codec.decode(codec.encode(joinRejected())));
    } // codec.decode(codec.encode(p)) equals p, for all 13

    @Test
    void chunkDataRoundTripsItsBytes() throws IOException {
        IPacket.ToClient.ChunkData expected = chunkData();
        IPacket.ToClient.ChunkData actual = (IPacket.ToClient.ChunkData) codec.decode(codec.encode(expected));

        assertEquals(expected.pos(), actual.pos());
        assertArrayEquals(expected.blocks(), actual.blocks());
    } // assertArrayEquals on blocks()

    @Test
    void floatsRoundTripBitExact() throws IOException {
        var expected = playerUpdate(0.1f, -0.0f, Float.MIN_VALUE, 11.7f);
        var actual = (IPacket.ToClient.PlayerUpdate) codec.decode(codec.encode(expected));
        assertPlayerUpdateEquals(expected, actual);
    } // PlayerUpdate with 0.1f, -0f, Float.MIN_VALUE, 11.7f → floatToRawIntBits equal

    @Test
    void unknownTagIsAnIOException() {
        assertThrows(IOException.class, () -> codec.decode(new byte[] {0}));
        assertThrows(IOException.class, () -> codec.decode(new byte[] {99}));
    } // codec.decode(new byte[]{ 99 }) throws IOException

    @Test
    void truncatedBodyIsAnEOFException() throws IOException {
        byte[] complete = codec.encode(playerInput());
        byte[] truncated = java.util.Arrays.copyOf(complete, complete.length - 1);

        assertThrows(EOFException.class, () -> codec.decode(truncated));
    } // encode a PlayerInput, drop the last byte, decode throws EOFException

    @Test
    void frameBodyIsOnePacket() throws IOException {
        byte[] bytes = codec.encode(playerInput());
        assertThrows(IOException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }

    @Test
    void oversizeArrayLengthIsRefused() {
        byte[] header = ByteBuffer.allocate(Byte.BYTES + 3 * Integer.BYTES)
                .put(PacketCodec.CHUNK_DATA)
                .putInt(1)
                .putInt(2)
                .putInt(PacketCodec.MAX_FRAME + 1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(header));
    } // CHUNK_DATA tag, pos, length MAX_FRAME+1 → IOException, no 2 GB allocation

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void nonFiniteFloatIsRefusedInEveryPlayerInputSlot(int slot) {
        float[] floats = new float[4]; // moveX, moveZ, pitch, yaw
        floats[slot] = Float.NaN;

        assertThrows(IOException.class, () -> codec.decode(playerInputBody(7L, floats)));
    } // NaN in each of PlayerInput's four floats → IOException

    @Test
    void negativeTickIsRefused() {
        assertThrows(IOException.class, () -> codec.decode(playerInputBody(-1L, new float[4])));
    } // PLAYER_INPUT tick -1 → IOException

    @ParameterizedTest
    @ValueSource(strings = {"", "01234567890123456", "ben\n"})
    void badUsernameIsRefusedOnDecode(String username) throws IOException {
        assertThrows(IOException.class, () -> codec.decode(rawJoinRequest(username, PacketCodec.PROTOCOL_VERSION)));
    } // empty, 17 characters, control character → IOException

    @Test
    void badUsernameIsRefusedOnEncode() {
        assertThrows(IOException.class, () -> codec.encode(new IPacket.Join.Request("", PacketCodec.PROTOCOL_VERSION)));
    } // write mirrors read: we never emit a username our own reader refuses

    @Test
    void incompatibleJoinRequestStillDecodes() throws IOException {
        int newer = PacketCodec.PROTOCOL_VERSION + 1;
        int older = PacketCodec.MIN_COMPATIBLE_VERSION - 1;

        assertEquals(new IPacket.Join.Request("ben", newer), codec.decode(rawJoinRequest("ben", newer)));
        assertEquals(new IPacket.Join.Request("ben", older), codec.decode(rawJoinRequest("ben", older)));
    } // the handshake layout is frozen: the server can read it and answer Join.Rejected

    @Test
    void compatibilityIsAnInclusiveRangeAndNewerNeverFits() {
        assertTrue(PacketCodec.isCompatible(PacketCodec.MIN_COMPATIBLE_VERSION));
        assertTrue(PacketCodec.isCompatible(PacketCodec.PROTOCOL_VERSION));
        assertTrue(!PacketCodec.isCompatible(PacketCodec.MIN_COMPATIBLE_VERSION - 1));
        assertTrue(!PacketCodec.isCompatible(PacketCodec.PROTOCOL_VERSION + 1));
    }

    @Test
    void overlongReasonIsRefused() {
        String reason = "x".repeat(PacketCodec.MAX_REASON_LENGTH + 1);

        assertThrows(IOException.class, () -> codec.encode(new IPacket.Join.Rejected(reason)));
        assertThrows(IOException.class, () -> codec.encode(new IPacket.ToServer.Disconnect(reason)));
    } // Rejected and Disconnect reasons are capped on write; read shares the same helper

    @Test
    void negativePlayerIdIsRefused() throws IOException {
        byte[] good = codec.encode(joinAccepted());
        byte[] bad = good.clone();
        bad[1] = (byte) 0x80; // sign bit of the big-endian playerId, right after the tag

        assertThrows(IOException.class, () -> codec.decode(bad));
    } // JOIN_ACCEPTED with a negative playerId → IOException

    @ParameterizedTest
    @ValueSource(ints = {-1, Chunk.SIZE_Y})
    void blockEditYOutOfRangeIsRefused(int y) {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 3 * Integer.BYTES + Byte.BYTES + 1)
                .put(PacketCodec.BLOCK_EDIT)
                .putLong(9L)
                .putInt(0)
                .putInt(y)
                .putInt(0)
                .put(Block.STONE.id())
                .put((byte) 1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // BLOCK_EDIT with y -1 and y 256 → IOException

    @Test
    void duplicateTagOrTypeIsRefusedAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PacketCodec.CodecRegistry(new PacketCodec.ChatCodec(), new PacketCodec.ChatCodec()));
    } // two codecs sharing a tag no longer silently overwrite each other

    @Test
    void everyPacketRecordHasACodec() {
        leafRecords(IPacket.class).forEach(type -> assertTrue(codec.handles(type), type.getName()));
    } // walks IPacket's sealed tree, so a new record without a codec fails here

    @Test
    void nonFiniteFloatIsRefusedInPlayerUpdate() {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 4 * Float.BYTES + 1)
                .put(PacketCodec.PLAYER_UPDATE)
                .putLong(1L)
                .putFloat(0f)
                .putFloat(Float.NaN)
                .putFloat(0f)
                .putFloat(0f)
                .put((byte) 1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // PLAYER_UPDATE with NaN in y → IOException

    @Test
    void negativeAckTickIsRefused() {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 4 * Float.BYTES + 1)
                .put(PacketCodec.PLAYER_UPDATE)
                .putLong(-1L)
                .putFloat(0f)
                .putFloat(0f)
                .putFloat(0f)
                .putFloat(0f)
                .put((byte) 1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // PLAYER_UPDATE ackTick -1 → IOException

    @ParameterizedTest
    @ValueSource(bytes = {PacketCodec.PLAYER_CONNECTED, PacketCodec.PLAYER_DISCONNECTED})
    void negativePlayerIdIsRefusedOnConnectAndDisconnect(byte tag) {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES)
                .put(tag)
                .putInt(-1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // negative playerId → IOException

    @ParameterizedTest
    @ValueSource(strings = {"", "hi\n"})
    void badChatIsRefused(String message) throws IOException {
        byte[] body = rawUtf(PacketCodec.CHAT, message);

        assertThrows(IOException.class, () -> codec.decode(body));
    } // empty and control-character chat → IOException

    @Test
    void overlongChatIsRefusedOnEncode() {
        String message = "x".repeat(PacketCodec.ChatCodec.MAX_CHAT_LENGTH + 1);

        assertThrows(IOException.class, () -> codec.encode(new IPacket.ToClient.Chat(message)));
    } // chat over the cap is refused on write too

    @Test
    void unknownBlockIdIsRefusedInBlockChanged() {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + 3 * Integer.BYTES + Byte.BYTES)
                .put(PacketCodec.BLOCK_CHANGED)
                .putInt(0)
                .putInt(64)
                .putInt(0)
                .put((byte) 127)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // BLOCK_CHANGED with id 127 → IOException

    @ParameterizedTest
    @ValueSource(ints = {-1, Chunk.SIZE_Y})
    void blockChangedYOutOfRangeIsRefused(int y) {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + 3 * Integer.BYTES + Byte.BYTES)
                .put(PacketCodec.BLOCK_CHANGED)
                .putInt(0)
                .putInt(y)
                .putInt(0)
                .put(Block.STONE.id())
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // BLOCK_CHANGED with y -1 and y 256 → IOException

    @Test
    void unknownBlockIdIsRefused() {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 3 * Integer.BYTES + Byte.BYTES + 1)
                .put(PacketCodec.BLOCK_EDIT)
                .putLong(9L)
                .putInt(0)
                .putInt(64)
                .putInt(0)
                .put((byte) 127)
                .put((byte) 1)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // BLOCK_EDIT with id 127 (highest real id is 53) → IOException, not a silent AIR

    @Test
    void wrongSizeChunkDataIsRefusedOnEncode() {
        var shortChunk = new IPacket.ToClient.ChunkData(new ChunkPos(1, 2), new byte[100]);

        assertThrows(IOException.class, () -> codec.encode(shortChunk));
    } // 100-byte ChunkData → encode throws, so we never emit what our own reader refuses

    @Test
    void wrongSizeChunkDataIsRefusedOnDecode() {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + 3 * Integer.BYTES + 100)
                .put(PacketCodec.CHUNK_DATA)
                .putInt(1)
                .putInt(2)
                .putInt(100)
                .put(new byte[100])
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // CHUNK_DATA with length 100 and 100 real bytes → IOException, the case a MAX_FRAME cap alone accepts

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void nonFiniteFloatIsRefused(float bad) {
        byte[] body = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 2 * Float.BYTES + 3 + 2 * Float.BYTES)
                .put(PacketCodec.PLAYER_INPUT)
                .putLong(7L)
                .putFloat(bad)
                .putFloat(0f)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .putFloat(0f)
                .putFloat(0f)
                .array();

        assertThrows(IOException.class, () -> codec.decode(body));
    } // PLAYER_INPUT with NaN / ±Infinity in moveX → IOException

    // helpers
    private byte[] playerInputBody(long tick, float[] f) {
        return ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 2 * Float.BYTES + 3 + 2 * Float.BYTES)
                .put(PacketCodec.PLAYER_INPUT)
                .putLong(tick)
                .putFloat(f[0])
                .putFloat(f[1])
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .putFloat(f[2])
                .putFloat(f[3])
                .array();
    }

    private byte[] rawUtf(byte tag, String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(tag);
            out.writeUTF(text);
        }
        return bytes.toByteArray();
    }

    private byte[] rawJoinRequest(String username, int version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(PacketCodec.JOIN_REQUEST);
            out.writeUTF(username);
            out.writeInt(version);
        }
        return bytes.toByteArray();
    }

    private static java.util.stream.Stream<Class<?>> leafRecords(Class<?> type) {
        if (type.isRecord()) {
            return java.util.stream.Stream.of(type);
        }
        Class<?>[] permitted = type.getPermittedSubclasses();
        return permitted == null
                ? java.util.stream.Stream.empty()
                : Arrays.stream(permitted).flatMap(PacketCodecTest::leafRecords);
    }

    // assertions
    void assertPlayerUpdateEquals(IPacket.ToClient.PlayerUpdate expected, IPacket.ToClient.PlayerUpdate actual) {
        assertEquals(expected.ackTick(), actual.ackTick());
        assertEquals(Float.floatToRawIntBits(expected.x()), Float.floatToRawIntBits(actual.x()));
        assertEquals(Float.floatToRawIntBits(expected.y()), Float.floatToRawIntBits(actual.y()));
        assertEquals(Float.floatToRawIntBits(expected.z()), Float.floatToRawIntBits(actual.z()));
        assertEquals(Float.floatToRawIntBits(expected.vy()), Float.floatToRawIntBits(actual.vy()));
        assertEquals(expected.onGround(), actual.onGround());
    }

    // fixtures
    public IPacket.ToServer.PlayerInput playerInput() {
        PlayerIntent intent = new PlayerIntent(1f, 0f, false, false, false, 0f, 90f);
        return new IPacket.ToServer.PlayerInput(7L, intent);
    }

    public IPacket.ToServer.BlockEdit blockEdit() {
        return new IPacket.ToServer.BlockEdit(9L, 0, 64, 0, Block.STONE, true);
    }

    public IPacket.ToServer.Disconnect disconnect() {
        return new IPacket.ToServer.Disconnect("quit");
    }

    public IPacket.ToClient.ChunkData chunkData() {
        byte[] data = new byte[Chunk.size()];
        for (int i = 0; i < Chunk.size(); i++) {
            data[i] = Block.STONE.id();
        }
        return new IPacket.ToClient.ChunkData(new ChunkPos(1, 2), data);
    }

    public IPacket.ToClient.ChunkUnload chunkUnload() {
        return new IPacket.ToClient.ChunkUnload(new ChunkPos(3, 4));
    }

    public IPacket.ToClient.BlockChanged blockChanged() {
        return new IPacket.ToClient.BlockChanged(5, 64, -7, Block.STONE);
    }

    public IPacket.ToClient.Chat chat() {
        return new IPacket.ToClient.Chat("hello");
    }

    public IPacket.ToClient.PlayerUpdate playerUpdate(float x, float y, float z, float vy) {
        return new IPacket.ToClient.PlayerUpdate(412L, x, y, z, vy, true);
    }

    public IPacket.ToClient.PlayerDisconnected playerDisconnected() {
        return new IPacket.ToClient.PlayerDisconnected(9);
    }

    public IPacket.ToClient.PlayerConnected playerConnected() {
        return new IPacket.ToClient.PlayerConnected(10);
    }

    public IPacket.Join.Request joinRequest() {
        return new IPacket.Join.Request("ben", 1);
    }

    public IPacket.Join.Accepted joinAccepted() {
        return new IPacket.Join.Accepted(7, 1234L, 0L, new PlayerState(0f, 65f, 0f, 0f, 0f));
    }

    public IPacket.Join.Rejected joinRejected() {
        return new IPacket.Join.Rejected("old client");
    }
}
