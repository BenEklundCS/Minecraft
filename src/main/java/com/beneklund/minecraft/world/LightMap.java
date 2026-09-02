package com.beneklund.minecraft.world;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class LightMap {
    public static int MAX_LEVEL = 15;
    public static int MIN_LEVEL = 0;

    private final byte[][] sections; // 16 slots of 4096, null when the section is uniform
    private final byte[] uniform; // 16 packed (sky << 4 | block) values, read when the slot is null

    public LightMap() {
        sections = new byte[Chunk.sectionCount()][];
        uniform = new byte[Chunk.sectionCount()];
    }

    public int sky(int i) {
        return (packed(i) >> 4) & 0x0F;
    }

    public int block(int i) {
        return packed(i) & 0x0F;
    }

    private byte packed(int i) {
        return sectionFor(i).map(cells -> cells[Chunk.offsetIn(i)]).orElse(uniform[Chunk.sectionOf(i)]);
    }

    private void write(int i, byte value) {
        if (sectionFor(i).isEmpty() && value == uniform[Chunk.sectionOf(i)]) return;
        sectionFor(i).orElseGet(() -> materialize(i))[Chunk.offsetIn(i)] = value;
    }

    private byte[] materialize(int i) {
        byte[] cells = new byte[ChunkSection.BLOCK_COUNT];
        Arrays.fill(cells, uniform[Chunk.sectionOf(i)]);
        sections[Chunk.sectionOf(i)] = cells;
        return cells;
    }

    private Optional<byte[]> sectionFor(int index) {
        return Optional.ofNullable(sections[Chunk.sectionOf(index)]);
    }

    private static boolean isUniform(byte[] cells) {
        for (byte cell : cells) {
            if (cell != cells[0]) return false;
        }
        return true;
    }

    // Run once after the engine has finished writing. A section whose cells all landed on the same value converts to a
    // uniform value.
    public void compact() {
        for (int i = 0; i < Chunk.size(); i += ChunkSection.BLOCK_COUNT) {
            int section = Chunk.sectionOf(i);
            sectionFor(i).filter(LightMap::isUniform).ifPresent(cells -> {
                uniform[section] = cells[0];
                sections[section] = null;
            });
        }
    }

    public void setSky(int i, int level) {
        write(i, (byte) ((packed(i) & 0x0F) | (level << 4)));
    }

    public void setBlock(int i, int level) {
        write(i, (byte) ((packed(i) & 0xF0) | (level & 0x0F)));
    }

    // Set the sky channel for a whole section at once. Folds into the uniform slot when the section
    // hasn't materialized, which is the whole point — open air above terrain costs one byte, not 4,096.
    public void fillSky(int section, int level) {
        byte[] cells = sections[section];
        if (cells == null) {
            uniform[section] = (byte) ((uniform[section] & 0x0F) | (level << 4));
            return;
        }
        for (int i = 0; i < cells.length; i++) cells[i] = (byte) ((cells[i] & 0x0F) | (level << 4));
    }

    // Always one chunk's worth, however the sections underneath happen to be stored.
    public int size() {
        return Chunk.size();
    }

    // test surface - how many sections are still holding an array.
    int materializedSections() {
        return (int) Arrays.stream(sections).filter(Objects::nonNull).count();
    }
}
