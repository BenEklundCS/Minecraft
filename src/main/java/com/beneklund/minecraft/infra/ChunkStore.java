package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IChunkStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.beneklund.minecraft.util.Log.LOGGER;

public class ChunkStore implements IChunkStore {
    private final Path basePath;

    public ChunkStore(long seed) {
        this.basePath = Paths.get("saves/", String.valueOf(seed));
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            LOGGER.error("Failed to create save directory.");
            throw new RuntimeException(e);
        }
    }


    @Override
    public void save(ChunkPos pos, Chunk chunk) {
        Path fullPath = getFullPath(pos);
        try {
            byte[] bytes = chunk.serialize();
            Files.write(fullPath, bytes);
        } catch (IOException e) {
            LOGGER.error("Failed to save chunk at {}_{}", pos.x(), pos.z());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Chunk> load(ChunkPos pos) {
        Path fullPath = getFullPath(pos);
        if (!Files.exists(fullPath)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(fullPath);
            return Optional.of(new Chunk(bytes));
        }
        catch (IOException e) {
            LOGGER.error("Failed to load chunk at {}_{}", pos.x(), pos.z());
            throw new RuntimeException(e);
        }
    }

    private Path getFullPath(ChunkPos pos) {
        return Paths.get(String.valueOf(basePath), "%d_%d.bin".formatted(pos.x(), pos.z()));
    }
}
