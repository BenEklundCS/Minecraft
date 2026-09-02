package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;

/** A section of a chunk that is always 16x16x16
 *  Enables support of extremely large chunks in {@link Chunk} because as max Y increases, the number of air-only sections increases.
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

    public static int index(int x, int y, int z) {
        return x + z * SIZE + y * SIZE * SIZE;
    }

    public boolean isEmpty() {
        return blocks == null || nonAirCount == 0;
    }

    // test surface
    protected byte[] getBlocks() {
        return blocks;
    }
}
