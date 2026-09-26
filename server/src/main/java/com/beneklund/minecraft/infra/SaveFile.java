package com.beneklund.minecraft.infra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * The on-disk envelope every save file shares: a 12-byte header followed by an opaque payload.
 *
 * <p>The header is three big-endian {@code int}s, {@link ByteBuffer}'s default order:
 *
 * <ol>
 *   <li>magic, identifying the file kind ({@code MC_C} for a chunk, {@code MC_P} for the player)
 *   <li>format version, checked by the caller so each store owns its own migration
 *   <li>payload length in bytes
 * </ol>
 *
 * <p>Writes go to a sibling {@code .tmp} file first and then replace the target with an atomic
 * rename, so a reader, or a restart after a crash mid-write, sees either the previous save or the
 * new one in full. A leftover {@code .tmp} is harmless; the next write truncates it.
 *
 * <p>Reads validate instead of throwing: a missing file, a wrong magic, or a length that disagrees
 * with the bytes on disk all come back as {@link Optional#empty()}, which the stores treat as "no
 * save". Only an I/O failure throws.
 *
 * @see <a
 *     href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html#ATOMIC_MOVE">
 *     StandardCopyOption.ATOMIC_MOVE</a>
 */
public final class SaveFile {
    private static final int HEADER_BYTES = 12;

    /** A validated payload and the format version it was written with. */
    public record Payload(int version, byte[] bytes) {}

    /** Writes header and payload to {@code path} through a temp file and an atomic move. */
    public static void write(Path path, int magic, int version, byte[] payload) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + payload.length);
        buf.putInt(magic).putInt(version).putInt(payload.length).put(payload);

        Path tmp = path.resolveSibling("%s.tmp".formatted(path.getFileName()));
        Files.write(tmp, buf.array());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Reads and validates the file at {@code path}.
     *
     * @return the payload, or empty if the file is missing, shorter than the header, carries a
     *     different magic, or has a payload length that disagrees with the header
     */
    public static Optional<Payload> read(Path path, int magic) throws IOException {
        if (!Files.exists(path)) return Optional.empty();

        byte[] all = Files.readAllBytes(path);
        if (all.length < HEADER_BYTES) return Optional.empty();

        ByteBuffer buf = ByteBuffer.wrap(all);

        if (buf.getInt() != magic) return Optional.empty();
        int version = buf.getInt();
        int length = buf.getInt();

        if (buf.remaining() != length) return Optional.empty();
        byte[] payload = new byte[length];
        buf.get(payload);
        return Optional.of(new Payload(version, payload));
    }
}
