package com.beneklund.minecraft.world;

import java.util.concurrent.atomic.AtomicReference;

public class Chunk {
    public static final int SIZE_XZ = 16;
    public static final int SIZE_Y = 256;
    private byte[] blocks = new byte[SIZE_XZ * SIZE_XZ * SIZE_Y]; // 16 * 16 * 256 == 65,536
    private final AtomicReference<ChunkState> state = new AtomicReference<>(ChunkState.UNLOADED);

    public Chunk(byte[] blocks) {
        this.blocks = blocks;
    }

    public byte getBlock(int x, int y, int z) {
        return this.blocks[index(x, y, z)];
    }

    public void setBlock(int x, int y, int z, byte id) {
        this.blocks[index(x, y, z)] = id;
    }

    private static int index(int x, int y, int z) {
        return x + z * SIZE_XZ + y * SIZE_XZ * SIZE_XZ; // x + z * 16 + y * 256
    }

    public ChunkState getState() {
        return state.get(); // volatile read - always sees the latest write
    }

    public boolean tryMarkDirty() {
        ChunkState current;
        do {
            current = state.get();
            if (!current.canTransitionTo(ChunkState.DIRTY)) return false;
        } while (!state.compareAndSet(current, ChunkState.DIRTY));
        return true;
    }
}
