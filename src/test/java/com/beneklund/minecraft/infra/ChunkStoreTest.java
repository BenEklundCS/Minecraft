package com.beneklund.minecraft.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// Same constraint as PlayerStoreTest: ChunkStore derives saves/<seed>/ from the seed, so
// these use a throwaway seed and clean up after themselves.
class ChunkStoreTest {
    private long seed;

    private ChunkStore storeWithFreshSeed() {
        seed = System.nanoTime();
        return new ChunkStore(seed);
    }

    private Path chunkFile(ChunkPos pos) {
        return Paths.get("saves", String.valueOf(seed), "%d_%d.bin".formatted(pos.x(), pos.z()));
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
                    // best effort — under the gitignored saves/ tree
                }
            });
        }
    }

    @Test
    void saveThenLoad_roundTripsBlockData() {
        ChunkStore store = storeWithFreshSeed();
        ChunkPos pos = new ChunkPos(3, -7);
        Chunk chunk = new Chunk();
        chunk.setBlock(0, 0, 0, Block.BEDROCK);
        chunk.setBlock(5, 64, 9, Block.DIAMOND_ORE);
        chunk.setBlock(15, 255, 15, Block.GLASS);

        store.save(pos, chunk);
        Optional<Chunk> read = store.load(pos);

        assertTrue(read.isPresent());
        assertEquals(Block.BEDROCK, read.get().getBlock(0, 0, 0));
        assertEquals(Block.DIAMOND_ORE, read.get().getBlock(5, 64, 9));
        assertEquals(Block.GLASS, read.get().getBlock(15, 255, 15));
        assertEquals(Block.AIR, read.get().getBlock(1, 1, 1), "untouched cells stay air");
    }

    @Test
    void load_withNoSavedChunkIsEmpty() {
        ChunkStore store = storeWithFreshSeed();

        assertTrue(store.load(new ChunkPos(0, 0)).isEmpty(), "an ungenerated chunk must report empty, not throw");
    }

    @Test
    void save_negativeCoordinatesRoundTrip() {
        ChunkStore store = storeWithFreshSeed();
        ChunkPos pos = new ChunkPos(-12, -34);
        Chunk chunk = new Chunk();
        chunk.setBlock(2, 30, 4, Block.STONE);

        store.save(pos, chunk);

        assertEquals(Block.STONE, store.load(pos).orElseThrow().getBlock(2, 30, 4));
    }

    // The nine files sitting in saves/42/ right now are in this format: 65,536 raw bytes,
    // no magic, no version. They must come back empty so the chunk regenerates, rather
    // than having their first 12 bytes read as a header.
    @Test
    void load_legacyHeaderlessChunkFileIsEmpty() throws IOException {
        ChunkStore store = storeWithFreshSeed();
        ChunkPos pos = new ChunkPos(0, 0);
        store.save(pos, new Chunk()); // creates the directory

        Files.write(chunkFile(pos), new byte[Chunk.SIZE_XZ * Chunk.SIZE_Y * Chunk.SIZE_XZ]);

        assertTrue(store.load(pos).isEmpty(), "pre-header saves must be rejected, not misread");
    }

    @Test
    void load_withAFutureVersionIsEmpty() throws IOException {
        ChunkStore store = storeWithFreshSeed();
        ChunkPos pos = new ChunkPos(1, 1);
        store.save(pos, new Chunk());

        SaveFile.write(chunkFile(pos), 0x4D435F43, 99, new Chunk().serialize());

        assertTrue(store.load(pos).isEmpty(), "version 99 must not be read as version 1");
    }
}
