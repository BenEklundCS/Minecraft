package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.util.Direction;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class Chunk {
    public static final int SIZE_XZ = 16;
    public static final int SIZE_Y = 256;
    private ChunkSection[] sections = new ChunkSection[SIZE_Y / ChunkSection.SIZE];
    private volatile LightMap light = null;
    private final AtomicReference<ChunkState> state = new AtomicReference<>(ChunkState.UNLOADED);
    // Tracks "has edits not yet written to disk" separately from the mesh-state machine,
    // which uses DIRTY only to signal "needs re-meshing" and clears it on the next tick.
    private volatile boolean needsPersisting = false;

    public Chunk() {}

    public Chunk(byte[] blocks) {
        sections = deserialize(blocks);
    }

    public Block getBlock(int index) {
        return getBlockImpl(index);
    }

    public Block getBlock(int x, int y, int z) {
        return getBlockImpl(index(x, y, z));
    }

    private Block getBlockImpl(int index) {
        return Block.fromId(sectionFor(index)
                .map(s -> s.get(index % ChunkSection.BLOCK_COUNT))
                .orElse(Block.AIR.id()));
    }

    public void setBlock(int x, int y, int z, Block block) {
        int index = index(x, y, z);
        Optional<ChunkSection> existing = sectionFor(index);
        if (existing.isEmpty() && block == Block.AIR) return;
        existing.orElseGet(() -> allocateSection(index)).set(index % ChunkSection.BLOCK_COUNT, block.id());
        needsPersisting = true;
    }

    public boolean needsPersisting() {
        return needsPersisting;
    }

    public void clearNeedsPersisting() {
        needsPersisting = false;
    }

    // Only reached from setBlock's orElseGet, which fires only when the slot is empty — so this
    // doesn't re-check before overwriting.
    private ChunkSection allocateSection(int index) {
        ChunkSection s = new ChunkSection();
        sections[index / ChunkSection.BLOCK_COUNT] = s;
        return s;
    }

    private Optional<ChunkSection> sectionFor(int index) {
        return Optional.ofNullable(sections[index / ChunkSection.BLOCK_COUNT]);
    }

    // Blocks in a chunk, and the length of a serialize() payload. Also the size of a LightMap,
    // which is a flat array addressed by index()
    public static int size() {
        return SIZE_XZ * SIZE_XZ * SIZE_Y; // 16 * 16 * 256 == 65,536 == sections.length * BLOCK_COUNT
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
        byte[] blocks = new byte[sections.length * ChunkSection.BLOCK_COUNT];
        int offset = 0;
        for (ChunkSection section : sections) {
            if (section == null) {
                for (int i = 0; i < ChunkSection.BLOCK_COUNT; i++) blocks[offset++] = Block.AIR.id();
            } else {
                for (int i = 0; i < ChunkSection.BLOCK_COUNT; i++) blocks[offset++] = section.get(i);
            }
        }
        return blocks;
    }

    private static ChunkSection[] deserialize(byte[] blocks) {
        ChunkSection[] sections = new ChunkSection[blocks.length / ChunkSection.BLOCK_COUNT];
        int offset = 0;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = new ChunkSection();
            for (int j = 0; j < ChunkSection.BLOCK_COUNT; j++) section.set(j, blocks[offset++]);
            sections[i] = section.isEmpty() ? null : section;
        }
        return sections;
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

    // The one place a LightMap is written after setLightData handed it over: LightEngine's removal
    // walk, patching out light whose emitter is gone. Callers check hasLight() first — a chunk that
    // has never been lit has nothing to patch.
    public void setBlockLight(int x, int y, int z, int level) {
        light.setBlock(index(x, y, z), level);
    }
}
