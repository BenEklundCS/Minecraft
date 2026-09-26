package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.util.Direction;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A 16 by 256 by 16 column of blocks, stored as sixteen {@link ChunkSection}s, plus its light and
 * its place in the {@link ChunkState} lifecycle.
 *
 * <p>Blocks are addressed by a flat index {@code x + z * 16 + y * 256}, so one horizontal layer is
 * contiguous and a 16-high section is a contiguous run of {@link ChunkSection#BLOCK_COUNT}. A
 * section that has never held a non-air block stays {@code null} and reads as air, so sky above
 * the terrain costs nothing.
 *
 * <p>Three pieces of state are safe to touch from several threads, each for its own reason:
 *
 * <ul>
 *   <li>{@link #getState()} and {@link #tryTransition} go through an {@link AtomicReference}, so a
 *       worker and the owning thread race on a compare-and-set and exactly one wins.
 *   <li>The {@link LightMap} is swapped whole through a volatile field; readers see the old map or
 *       the new one.
 *   <li>{@link #needsPersisting()} is volatile and set by every {@link #setBlock}, independent of
 *       the state machine, which uses {@link ChunkState#DIRTY} only for "needs remeshing".
 * </ul>
 *
 * <p>Block reads and writes themselves are unsynchronised. A meshing worker can read a block
 * mid-edit; {@link ChunkWithNeighbors} explains why that is tolerated.
 *
 * @see <a href="https://minecraft.wiki/w/Chunk">Minecraft Wiki: Chunk</a>
 */
public class Chunk {
    public static final int SIZE_XZ = 16;
    public static final int SIZE_Y = 256;
    private final ChunkSection[] sections = new ChunkSection[sectionCount()];
    private volatile LightMap light = null;
    private final AtomicReference<ChunkState> state = new AtomicReference<>(ChunkState.UNLOADED);
    // Tracks "has edits not yet written to disk" separately from the mesh-state machine,
    // which uses DIRTY only to signal "needs re-meshing" and clears it on the next tick.
    private volatile boolean needsPersisting = false;

    /** An all-air chunk in {@link ChunkState#UNLOADED}. */
    public Chunk() {}

    /**
     * A chunk rebuilt from a {@link #serialize()} payload. Sections that decode to all air are
     * dropped back to {@code null}.
     *
     * @throws IllegalArgumentException if {@code blocks} isn't a whole number of sections
     */
    public Chunk(byte[] blocks) {
        if (blocks.length % ChunkSection.BLOCK_COUNT != 0) {
            throw new IllegalArgumentException("chunk blocks must be a whole number of sections, got " + blocks.length);
        }
        deserialize(blocks);
    }

    public Block getBlock(int index) {
        return getBlockImpl(index);
    }

    public Block getBlock(int x, int y, int z) {
        return getBlockImpl(index(x, y, z));
    }

    private Block getBlockImpl(int index) {
        return Block.fromId(sectionFor(index).map(s -> s.get(offsetIn(index))).orElse(Block.AIR.id()));
    }

    /**
     * Writes a block at chunk-local coordinates and marks the chunk as needing a save. Writing air
     * into an unallocated section is a no-op and leaves the flag alone.
     */
    public void setBlock(int x, int y, int z, Block block) {
        int index = index(x, y, z);
        Optional<ChunkSection> existing = sectionFor(index);
        if (existing.isEmpty() && block == Block.AIR) return;
        existing.orElseGet(() -> allocateSection(index)).set(offsetIn(index), block.id());
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
        sections[sectionOf(index)] = s;
        return s;
    }

    private Optional<ChunkSection> sectionFor(int index) {
        return Optional.ofNullable(sections[sectionOf(index)]);
    }

    /** Whether the section containing layer {@code y} holds only air. */
    public boolean sectionEmptyAt(int y) {
        return sectionFor(index(0, y, 0)).map(ChunkSection::isEmpty).orElse(true);
    }

    /**
     * Blocks in a chunk (65,536), and the length of a {@link #serialize()} payload. Also the number
     * of cells a {@link LightMap} covers, though it stores them per section.
     */
    public static int size() {
        return SIZE_XZ * SIZE_XZ * SIZE_Y;
    }

    /** The flat block index {@code x + z * 16 + y * 256}; {@link #x}, {@link #y}, {@link #z} invert it. */
    protected static int index(int x, int y, int z) {
        return x + z * SIZE_XZ + y * SIZE_XZ * SIZE_XZ;
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

    protected static int sectionOf(int index) {
        return index / ChunkSection.BLOCK_COUNT;
    }

    protected static int offsetIn(int index) {
        return index % ChunkSection.BLOCK_COUNT;
    }

    protected static int sectionCount() {
        return SIZE_Y / ChunkSection.SIZE;
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

    /** The flat index one step from {@code index} in {@code dir}, or -1 if that leaves the chunk. */
    protected static int neighborIndex(int index, Direction dir) {
        int nx = x(index) + dir.dx();
        int ny = y(index) + dir.dy();
        int nz = z(index) + dir.dz();
        if (!inBounds(nx, ny, nz)) return -1;
        return index(nx, ny, nz);
    }

    public ChunkState getState() {
        return state.get();
    }

    /**
     * Moves to {@code next} if {@link ChunkState#canTransitionTo} allows it from the current state.
     *
     * <p>A failed compare-and-set means another thread changed the state between the read and the
     * write, so the loop re-reads and re-checks the rule against the new state. It returns {@code
     * false} as soon as the current state forbids the move. Every pipeline job enters and exits
     * through this and abandons its work on {@code false}, which is how a chunk evicted mid-job
     * drops out of the pipeline.
     *
     * @return whether this call performed the transition
     * @see <a href="https://en.wikipedia.org/wiki/Compare-and-swap">Compare-and-swap</a>
     */
    public boolean tryTransition(ChunkState next) {
        ChunkState current;
        do {
            current = state.get();
            if (!current.canTransitionTo(next)) return false;
        } while (!state.compareAndSet(current, next));
        return true;
    }

    /** Every block id in flat index order, air filled in for unallocated sections. */
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

    private void deserialize(byte[] blocks) {
        int offset = 0;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = new ChunkSection();
            for (int j = 0; j < ChunkSection.BLOCK_COUNT; j++) section.set(j, blocks[offset++]);
            sections[i] = section.isEmpty() ? null : section;
        }
    }

    /**
     * Publishes a freshly computed {@link LightMap}. The light getters throw until the first call;
     * check {@link #hasLight()}.
     */
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

    /**
     * Patches one block-light cell in the published map. The only write to a {@link LightMap} after
     * {@link #setLightData}: {@link LightEngine#removeBlockLight} clearing light whose emitter is
     * gone. Callers check {@link #hasLight()} first, since an unlit chunk has nothing to patch.
     */
    public void setBlockLight(int x, int y, int z, int level) {
        light.setBlock(index(x, y, z), level);
    }
}
