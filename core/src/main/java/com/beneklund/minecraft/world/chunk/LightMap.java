package com.beneklund.minecraft.world.chunk;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Sky and block light for every cell of one chunk, packed as two 4-bit levels per byte: {@code sky
 * << 4 | block}.
 *
 * <p>Storage is per {@link ChunkSection}. A section whose cells all share one packed value is a
 * single byte in {@code uniform}; the first write that breaks uniformity materialises a 4096-byte
 * array filled with that value. {@link LightEngine} writes freely, then calls {@link #compact()}
 * to fold sections that ended up uniform back to one byte, so open sky and unlit rock cost one
 * byte per section.
 *
 * <p>Cells use {@link Chunk}'s flat index. A map is written by one thread while being computed,
 * then published whole with {@link Chunk#setLightData}; the only later writer is {@link
 * LightEngine#removeBlockLight}.
 */
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

    /** Folds every materialised section whose cells all hold one value back to a uniform byte. */
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

    /**
     * Sets the sky level of every cell in {@code section}, keeping block light. An unmaterialised
     * section takes it as its uniform byte, so open air above terrain stays one byte.
     */
    public void fillSky(int section, int level) {
        byte[] cells = sections[section];
        if (cells == null) {
            uniform[section] = (byte) ((uniform[section] & 0x0F) | (level << 4));
            return;
        }
        for (int i = 0; i < cells.length; i++) cells[i] = (byte) ((cells[i] & 0x0F) | (level << 4));
    }

    /** Cells covered: always {@link Chunk#size()}, however the sections are stored. */
    public int size() {
        return Chunk.size();
    }

    // test surface - how many sections are still holding an array.
    int materializedSections() {
        return (int) Arrays.stream(sections).filter(Objects::nonNull).count();
    }
}
