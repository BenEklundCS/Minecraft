package com.beneklund.minecraft.infra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class SaveFile {
    private static final int HEADER_BYTES = 12;

    public record Payload(int version, byte[] bytes) {}

    public static void write(Path path, int magic, int version, byte[] payload) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + payload.length);
        buf.putInt(magic).putInt(version).putInt(payload.length).put(payload);

        Path tmp = path.resolveSibling("%s.tmp".formatted(path.getFileName()));
        Files.write(tmp, buf.array());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

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
