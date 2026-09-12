package com.beneklund.minecraft.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.player.PlayerState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// PlayerStore builds its own path from the seed (saves/<seed>/level.dat), so there's no
// way to point it at a @TempDir. These use a throwaway seed per test and delete the
// directory afterwards. If the constructor ever takes a base path, switch to @TempDir.
class PlayerStoreTest {
    private static final int MAGIC = 0x4D435F50; // MC_P

    private long seed;

    private PlayerStore storeWithFreshSeed() {
        seed = System.nanoTime();
        return new PlayerStore(seed);
    }

    private Path levelDat() {
        return Paths.get("saves", String.valueOf(seed), "level.dat");
    }

    @AfterEach
    void deleteSaveDir() throws IOException {
        Path dir = Paths.get("saves", String.valueOf(seed));
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort — the directory is under the gitignored saves/ tree
                }
            });
        }
    }

    @Test
    void saveThenLoad_roundTripsEveryField() {
        PlayerStore store = storeWithFreshSeed();
        PlayerState written = new PlayerState(1.5f, 64.25f, -3.75f, 90.5f, -12.25f);

        store.save(written);
        Optional<PlayerState> read = store.load();

        assertTrue(read.isPresent());
        assertEquals(written, read.get(), "all five floats must survive, in order");
    }

    @Test
    void load_withNoSavedFileIsEmpty() {
        PlayerStore store = storeWithFreshSeed();

        assertTrue(store.load().isEmpty(), "a fresh world has no level.dat and must fall back to default spawn");
    }

    @Test
    void save_overwritesAPreviousState() {
        PlayerStore store = storeWithFreshSeed();

        store.save(new PlayerState(1, 1, 1, 1, 1));
        store.save(new PlayerState(2, 2, 2, 2, 2));

        assertEquals(new PlayerState(2, 2, 2, 2, 2), store.load().orElseThrow());
    }

    // A level.dat written by a future build. Rejecting it is what stops the old reader
    // from interpreting new bytes as the current layout.
    @Test
    void load_withAFutureVersionIsEmpty() throws IOException {
        PlayerStore store = storeWithFreshSeed();
        store.save(new PlayerState(1, 2, 3, 4, 5)); // creates the directory

        ByteBuffer payload = ByteBuffer.allocate(5 * Float.BYTES);
        payload.putFloat(1).putFloat(2).putFloat(3).putFloat(4).putFloat(5);
        SaveFile.write(levelDat(), MAGIC, 99, payload.array());

        assertTrue(store.load().isEmpty(), "version 99 must not be read as version 1");
    }

    @Test
    void load_withWrongMagicIsEmpty() throws IOException {
        PlayerStore store = storeWithFreshSeed();
        store.save(new PlayerState(1, 2, 3, 4, 5));

        ByteBuffer payload = ByteBuffer.allocate(5 * Float.BYTES);
        SaveFile.write(levelDat(), 0x4D435F43, 1, payload.array()); // MC_C, the chunk magic

        assertTrue(store.load().isEmpty(), "a chunk file dropped in as level.dat must not parse");
    }

    // The version gate only fires when someone remembers to bump VERSION. A payload of the
    // wrong length under the *current* version reaches the ByteBuffer reads directly, and
    // BufferUnderflowException is unchecked — catch (IOException) in load() won't hold it.
    @Test
    void load_withShortPayloadAtCurrentVersionIsEmpty() throws IOException {
        PlayerStore store = storeWithFreshSeed();
        store.save(new PlayerState(1, 2, 3, 4, 5));

        SaveFile.write(levelDat(), MAGIC, 1, new byte[8]); // 2 floats where 5 are expected

        assertTrue(store.load().isEmpty());
    }
}
