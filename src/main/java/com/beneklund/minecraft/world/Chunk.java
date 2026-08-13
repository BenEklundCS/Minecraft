package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.util.Direction;
import java.util.concurrent.atomic.AtomicReference;

public class Chunk {
    public static final int SIZE_XZ = 16;
    public static final int SIZE_Y = 256;
    // Storage stays a packed byte[] (1 byte/block) for memory and cache; Block is the API face.
    private byte[] blocks = new byte[size()];
    private volatile LightMap light = null;
    private final AtomicReference<ChunkState> state = new AtomicReference<>(ChunkState.UNLOADED);
    // Tracks "has edits not yet written to disk" separately from the mesh-state machine,
    // which uses DIRTY only to signal "needs re-meshing" and clears it on the next tick.
    private volatile boolean needsPersisting = false;

    public Chunk() {}

    public Chunk(byte[] blocks) {
        this.blocks = blocks;
    }

    public static int size() {
        return SIZE_XZ * SIZE_XZ * SIZE_Y; // 16 * 16 * 256 == 65,536
    }

    public Block getBlock(int x, int y, int z) {
        return getBlock(index(x, y, z));
    }

    public void setBlock(int x, int y, int z, Block block) {
        blocks[index(x, y, z)] = block.id();
        needsPersisting = true;
    }

    public boolean needsPersisting() {
        return needsPersisting;
    }

    public void clearNeedsPersisting() {
        needsPersisting = false;
    }

    protected static int index(int x, int y, int z) {
        return x + z * SIZE_XZ + y * SIZE_XZ * SIZE_XZ; // x + z * 16 + y * 256
    }

    protected static int x(int index) {
        return index % SIZE_XZ;
    }

    protected static int y(int index) {
        return index / (Chunk.SIZE_XZ * Chunk.SIZE_XZ);
    }

    protected static int z(int index) {
        return (index / SIZE_XZ) % SIZE_XZ;
    }

    public static boolean inBounds(int x, int y, int z) {
        return inXZRange(x) && inYRange(y) && inXZRange(z);
    }

    public static boolean inYRange(int y) {
        return y >= 0 && y < SIZE_Y;
    }

    public static boolean inXZRange(int v) {
        return v >= 0 && v < SIZE_XZ;
    }

    protected static int neighborIndex(int index, Direction dir) {
        int nx = x(index) + dir.dx();
        int ny = y(index) + dir.dy();
        int nz = z(index) + dir.dz();
        if (!inBounds(nx, ny, nz)) return -1;
        return index(nx, ny, nz);
    }

    // Read by packed index. Storage is index-addressed underneath, so a caller already holding an
    // index shouldn't have to decode to three coordinates just to let index() re-pack them.
    protected Block getBlock(int index) {
        return Block.fromId(blocks[index]);
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

    public byte[] serialize() {
        return blocks;
    }

    public void setLightData(LightMap next) {
        light = next;
    }

    public boolean hasLight() {
        return light != null;
    }

    public int getSkyLight(int x, int y, int z) {
        return light.sky(index(x, y, z));
    }

    public int getBlockLight(int x, int y, int z) {
        return light.block(index(x, y, z));
    }
}
