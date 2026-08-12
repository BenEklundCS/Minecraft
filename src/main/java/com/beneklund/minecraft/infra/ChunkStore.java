package com.beneklund.minecraft.infra;

import static com.beneklund.minecraft.util.Log.IO;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IChunkStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ChunkStore implements IChunkStore {
    private static final int MAGIC = 0x4D435F43; // MC_C
    private static final int VERSION = 1;

    private final Path basePath;

    public ChunkStore(long seed) {
        basePath = Paths.get("saves/", String.valueOf(seed));
        try {
            Files.createDirectories(basePath);
            IO.info("save directory: {}", basePath.toAbsolutePath());
        } catch (IOException e) {
            IO.error("Failed to create save directory.");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(ChunkPos pos, Chunk chunk) {
        try {
            SaveFile.write(getFullPath(pos), MAGIC, VERSION, chunk.serialize());
            IO.trace("saved chunk {}", pos);
        } catch (IOException e) {
            IO.error("Failed to save chunk at {}_{}", pos.x(), pos.z());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Chunk> load(ChunkPos pos) {
        try {
            Optional<Chunk> loaded = SaveFile.read(getFullPath(pos), MAGIC)
                    .filter(p -> p.version() == VERSION)
                    .map(p -> new Chunk(p.bytes()));
            IO.trace("chunk {} {} on disk", pos, loaded.isPresent() ? "found" : "not found");
            return loaded;
        } catch (IOException e) {
            IO.error("Failed to load chunk at {}_{}", pos.x(), pos.z());
            throw new RuntimeException(e);
        }
    }

    private Path getFullPath(ChunkPos pos) {
        return Paths.get(String.valueOf(basePath), "%d_%d.bin".formatted(pos.x(), pos.z()));
    }
}
