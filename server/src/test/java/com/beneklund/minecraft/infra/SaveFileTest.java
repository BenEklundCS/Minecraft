package com.beneklund.minecraft.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// SaveFile takes explicit paths, so these run entirely inside a @TempDir with no save
// directory involved. Everything that isn't a well-formed file with a matching magic has
// to come back empty rather than throwing — callers treat empty as "regenerate it".
class SaveFileTest {
    private static final int MAGIC = 0x54455354; // "TEST"
    private static final int LEGACY_CHUNK_BYTES = 16 * 256 * 16;

    @TempDir
    Path dir;

    @Test
    void writeThenRead_roundTripsVersionAndPayload() throws IOException {
        Path file = dir.resolve("round.bin");
        byte[] payload = {1, 2, 3, 4, 5};

        SaveFile.write(file, MAGIC, 7, payload);
        Optional<SaveFile.Payload> read = SaveFile.read(file, MAGIC);

        assertTrue(read.isPresent());
        assertEquals(7, read.get().version());
        assertArrayEquals(payload, read.get().bytes());
    }

    @Test
    void write_leavesNoTempFileBehind() throws IOException {
        Path file = dir.resolve("clean.bin");

        SaveFile.write(file, MAGIC, 1, new byte[] {9});

        try (var entries = Files.list(dir)) {
            assertEquals(1, entries.count(), "the .tmp staging file should have been moved, not copied, into place");
        }
    }

    @Test
    void write_replacesAnExistingFile() throws IOException {
        Path file = dir.resolve("twice.bin");

        SaveFile.write(file, MAGIC, 1, new byte[] {1, 1, 1, 1});
        SaveFile.write(file, MAGIC, 2, new byte[] {2, 2});

        Optional<SaveFile.Payload> read = SaveFile.read(file, MAGIC);
        assertTrue(read.isPresent());
        assertEquals(2, read.get().version());
        assertArrayEquals(new byte[] {2, 2}, read.get().bytes());
    }

    @Test
    void read_missingFileIsEmpty() throws IOException {
        assertTrue(SaveFile.read(dir.resolve("nope.bin"), MAGIC).isEmpty());
    }

    @Test
    void read_wrongMagicIsEmpty() throws IOException {
        Path file = dir.resolve("alien.bin");
        SaveFile.write(file, 0xDEADBEEF, 1, new byte[] {1, 2, 3});

        assertTrue(SaveFile.read(file, MAGIC).isEmpty(), "a file from another store must not parse as ours");
    }

    // The headerless format ChunkStore used before this change. 65,536 raw bytes with no
    // magic — has to be rejected, not read as though the first 12 bytes were a header.
    @Test
    void read_legacyHeaderlessFileIsEmpty() throws IOException {
        Path file = dir.resolve("legacy.bin");
        Files.write(file, new byte[LEGACY_CHUNK_BYTES]);

        assertTrue(SaveFile.read(file, MAGIC).isEmpty());
    }

    // Header claims more payload than the file actually holds — what a write killed
    // partway through leaves behind.
    @Test
    void read_truncatedPayloadIsEmpty() throws IOException {
        Path file = dir.resolve("cut.bin");
        ByteBuffer buf = ByteBuffer.allocate(12 + 4);
        buf.putInt(MAGIC).putInt(1).putInt(999).put(new byte[] {1, 2, 3, 4});
        Files.write(file, buf.array());

        assertTrue(SaveFile.read(file, MAGIC).isEmpty());
    }

    // A file too short to even hold the 12-byte header. read() must answer empty rather
    // than letting a BufferUnderflowException out — it's unchecked, so the callers'
    // catch (IOException) would not stop it reaching the game thread.
    @Test
    void read_fileShorterThanHeaderIsEmpty() throws IOException {
        Path file = dir.resolve("stub.bin");
        Files.write(file, ByteBuffer.allocate(4).putInt(MAGIC).array());

        assertTrue(SaveFile.read(file, MAGIC).isEmpty());
    }

    @Test
    void read_emptyFileIsEmpty() throws IOException {
        Path file = dir.resolve("zero.bin");
        Files.write(file, new byte[0]);

        assertTrue(SaveFile.read(file, MAGIC).isEmpty());
    }
}
