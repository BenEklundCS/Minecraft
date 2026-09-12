package com.beneklund.minecraft.infra;

import static com.beneklund.minecraft.util.Log.IO;

import com.beneklund.minecraft.player.IPlayerStore;
import com.beneklund.minecraft.player.PlayerState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class PlayerStore implements IPlayerStore {
    private static final int MAGIC = 0x4D435F50; // MC_P
    private static final int VERSION = 1;
    private static final int BYTES = 5 * Float.BYTES;

    private final Path basePath;

    public PlayerStore(long seed) {
        Path basePath = Paths.get("saves", String.valueOf(seed));
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.basePath = basePath.resolve("level.dat");
    }

    @Override
    public void save(PlayerState s) {
        ByteBuffer buf = ByteBuffer.allocate(BYTES);
        buf.putFloat(s.x()).putFloat(s.y()).putFloat(s.z()).putFloat(s.pitch()).putFloat(s.yaw());
        try {
            SaveFile.write(basePath, MAGIC, VERSION, buf.array());
        } catch (IOException e) {
            IO.error("Failed to save player state.");
        }
    }

    @Override
    public Optional<PlayerState> load() {
        try {
            return SaveFile.read(basePath, MAGIC)
                    .filter(p -> p.version() == VERSION)
                    .filter(p -> p.bytes().length == BYTES)
                    .map(p -> {
                        ByteBuffer buf = ByteBuffer.wrap(p.bytes());
                        return new PlayerState(
                                buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat());
                    });
        } catch (IOException e) {
            IO.error("Failed to load player state.");
            return Optional.empty();
        }
    }
}
