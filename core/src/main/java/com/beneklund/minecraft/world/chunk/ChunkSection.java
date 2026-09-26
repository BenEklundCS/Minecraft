package com.beneklund.minecraft.world.chunk;

import com.beneklund.minecraft.block.Block;

/**
 * A 16 by 16 by 16 cube of block ids, the unit a {@link Chunk} allocates storage in.
 *
 * <p>The backing array is created on the first non-air write, and a count of non-air blocks makes
 * {@link #isEmpty()} constant time. Most of a 256-high column is air above the terrain, so most
 * sections of a typical chunk never allocate. Taller worlds cost only the sections that hold
 * blocks.
 */
public class ChunkSection {
    public static final int SIZE = 16;
    public static final int BLOCK_COUNT = SIZE * SIZE * SIZE; // 4096

    private byte[] blocks;
    private int nonAirCount;

    public ChunkSection() {}

    public byte get(int index) {
        return blocks == null ? Block.AIR.id() : blocks[index];
    }

    public void set(int index, byte id) {
        if (blocks == null) {
            if (id == Block.AIR.id()) return;
            blocks = new byte[BLOCK_COUNT];
        }
        byte previous = blocks[index];
        if (previous != Block.AIR.id() && id == Block.AIR.id()) nonAirCount--;
        else if (previous == Block.AIR.id() && id != Block.AIR.id()) nonAirCount++;
        blocks[index] = id;
    }

    /** The index within a section, same layout as {@link Chunk}: x fastest, then z, then y. */
    public static int index(int x, int y, int z) {
        return x + z * SIZE + y * SIZE * SIZE;
    }

    /** Whether every block is air. A section emptied by edits keeps its array. */
    public boolean isEmpty() {
        return blocks == null || nonAirCount == 0;
    }

    // test surface
    protected byte[] getBlocks() {
        return blocks;
    }
}
