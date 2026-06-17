package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import java.util.concurrent.atomic.AtomicReference;

public class Chunk {
    public static final int SIZE_XZ = 16;
    public static final int SIZE_Y = 256;
    // Storage stays a packed byte[] (1 byte/block) for memory and cache; Block is the API face.
    private final byte[] blocks = new byte[SIZE_XZ * SIZE_XZ * SIZE_Y]; // 16 * 16 * 256 == 65,536
    private final AtomicReference<ChunkState> state = new AtomicReference<>(ChunkState.UNLOADED);

    public Chunk() {}

    public Block getBlock(int x, int y, int z) {
        return Block.fromId(blocks[index(x, y, z)]);
    }

    public void setBlock(int x, int y, int z, Block block) {
        blocks[index(x, y, z)] = block.id();
    }

    private static int index(int x, int y, int z) {
        return x + z * SIZE_XZ + y * SIZE_XZ * SIZE_XZ; // x + z * 16 + y * 256
    }

    public ChunkState getState() {
        return state.get(); // volatile read - always sees the latest write
    }

    // Attempt to advance to `next` from whatever state is currently set.
    // Returns false immediately if the current state doesn't allow the transition.
    // Loops on CAS failure — that means another thread just changed the state,
    // so we re-read and re-check rather than blindly retrying with a stale value.
    public boolean tryTransition(ChunkState next) {
        ChunkState current;
        do {
            current = state.get();
            if (!current.canTransitionTo(next)) return false;
        } while (!state.compareAndSet(current, next));
        return true;
    }
}
