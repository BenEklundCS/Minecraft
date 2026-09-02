package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.util.Direction;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class LightEngine {
    private final BlockRegistry registry;

    public LightEngine(BlockRegistry registry) {
        this.registry = registry;
    }

    protected enum Channel {
        SKY,
        BLOCK;

        int read(LightMap m, int i) {
            return (this == SKY) ? m.sky(i) : m.block(i);
        }

        void write(LightMap m, int i, int level) {
            if ((this == SKY)) {
                m.setSky(i, level);
            } else {
                m.setBlock(i, level);
            }
        }

        boolean fallsFreely() {
            return this == SKY;
        }
    }

    public LightMap compute(ChunkWithNeighbors cn) {
        LightMap lightMap = new LightMap();
        Chunk chunk = cn.center();
        Deque<Integer> sky = new ArrayDeque<>();
        computeSkyColumnPass(cn, lightMap, sky);
        computeAcrossChunksPass(cn, lightMap, sky, Channel.SKY);
        computePropagationPass(chunk, lightMap, sky, Channel.SKY);

        Deque<Integer> block = new ArrayDeque<>();
        computeBlocksPass(chunk, lightMap, block);
        computeAcrossChunksPass(cn, lightMap, block, Channel.BLOCK);
        computePropagationPass(chunk, lightMap, block, Channel.BLOCK);
        // Every write above materialized whatever section it touched. Fold back the ones that ended
        // up a single value — open sky over terrain, unlit rock — now that nothing else will write.
        lightMap.compact();
        return lightMap;
    }

    private void computeSkyColumnPass(ChunkWithNeighbors cn, LightMap map, Deque<Integer> queue) {
        boolean[] open = new boolean[Chunk.SIZE_XZ * Chunk.SIZE_XZ];
        Arrays.fill(open, true);
        int openColumns = open.length;

        for (int s = Chunk.sectionCount() - 1; s >= 0; s--) {
            int base = s * ChunkSection.SIZE; // lowest y in this section

            // All air, and nothing above it has closed a single column yet, so every cell is level 15.
            // Store that as the section's uniform value: no array, no per-cell walk. Only the floor row
            // goes on the queue — every other face of the section borders cells that are already at
            // MAX_LEVEL, so propagating from them could only hand out a level nobody would take.
            //
            // openColumns is the whole guard. An empty section under a roof is still all air, and
            // filling it would light the underside of every overhang — a bug you cannot see at noon.
            if (openColumns == open.length && cn.center().sectionEmptyAt(base)) {
                map.fillSky(s, LightMap.MAX_LEVEL);
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                    for (int z = 0; z < Chunk.SIZE_XZ; z++) queue.add(Chunk.index(x, base, z));
                }
                continue;
            }

            for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                    int column = x + z * Chunk.SIZE_XZ;
                    if (!open[column]) continue;
                    for (int y = base + ChunkSection.SIZE - 1; y >= base; y--) {
                        if (registry.get(cn.blockAt(x, y, z)).opaque()) {
                            open[column] = false;
                            openColumns--;
                            break;
                        }
                        int index = Chunk.index(x, y, z);
                        queue.add(index);
                        map.setSky(index, LightMap.MAX_LEVEL);
                    }
                }
            }
        }
    }

    private void computeBlocksPass(Chunk c, LightMap map, Deque<Integer> queue) {
        for (int base = 0; base < Chunk.size(); base += ChunkSection.BLOCK_COUNT) {
            if (c.sectionEmptyAt(Chunk.y(base))) continue;
            for (int i = base; i < base + ChunkSection.BLOCK_COUNT; i++) {
                int level = registry.get(c.getBlock(i)).lightLevel();
                if (level > 0) {
                    map.setBlock(i, level);
                    queue.add(i);
                }
            }
        }
    }

    private void computeAcrossChunksPass(
            ChunkWithNeighbors cn, LightMap map, Deque<Integer> propagationQueue, Channel channel) {
        int last = Chunk.SIZE_XZ - 1;
        for (int i = 0; i < Chunk.SIZE_XZ; i++) {
            seedSeam(cn, map, propagationQueue, channel, i, 0, i, -1); // north
            seedSeam(cn, map, propagationQueue, channel, i, last, i, last + 1); // south
            seedSeam(cn, map, propagationQueue, channel, last, i, last + 1, i); // east
            seedSeam(cn, map, propagationQueue, channel, 0, i, -1, i); // west
        }
    }

    private void seedSeam(
            ChunkWithNeighbors cn,
            LightMap map,
            Deque<Integer> queue,
            Channel channel,
            int insideX,
            int insideZ,
            int outsideX,
            int outsideZ) {
        if (cn.resolve(outsideX, outsideZ).isEmpty()) return;
        Chunk c = cn.center();
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            int light = (channel == Channel.SKY)
                    ? cn.skyLightAt(outsideX, y, outsideZ)
                    : cn.blockLightAt(outsideX, y, outsideZ);
            seedBorder(c, map, queue, channel, Chunk.index(insideX, y, insideZ), light - 1);
        }
    }

    private void seedBorder(Chunk c, LightMap map, Deque<Integer> queue, Channel channel, int index, int next) {
        if (registry.get(c.getBlock(index)).opaque()) return;
        propagateAndSet(index, next, queue, map, channel);
    }

    private void computePropagationPass(Chunk c, LightMap map, Deque<Integer> propagationQueue, Channel channel) {
        while (!propagationQueue.isEmpty()) {
            int i = propagationQueue.poll();
            int light = channel.read(map, i);

            for (Direction dir : Direction.DIRECTIONS) {
                int ni = Chunk.neighborIndex(i, dir);
                if (ni < 0) continue;
                if (registry.get(c.getBlock(ni)).opaque()) continue;
                int next = (channel.fallsFreely() && dir == Direction.DOWN && light == LightMap.MAX_LEVEL)
                        ? LightMap.MAX_LEVEL
                        : light - 1;
                propagateAndSet(ni, next, propagationQueue, map, channel);
            }
        }
    }

    private void propagateAndSet(int index, int next, Deque<Integer> queue, LightMap map, Channel channel) {
        if (next > channel.read(map, index)) {
            channel.write(map, index, next);
            queue.add(index);
        }
    }

    // Removal counterpart to the flood above, and the only part of this class that works in world
    // coordinates on already-stored LightMaps rather than building a fresh one.
    //
    // compute() rebuilds a chunk's map from nothing, so an emitter removed inside a chunk needs no
    // help — the next remesh simply doesn't find it. The neighbours are the problem. seedSeam reads
    // their stored maps, and a neighbour that was lit by this emitter isn't remeshing (the edit
    // wasn't in it), so it keeps claiming the light and feeds it straight back over the seam, one
    // level down. Recomputing the edited chunk alone can't win: propagateAndSet only ever raises a
    // level, so nothing in a from-scratch pass can lower what the seam hands it.
    //
    // Two phases, because "dimmer than where I came from" is the only local test for "this light
    // was mine". Walk outward zeroing those cells; a cell as bright or brighter belongs to some
    // other emitter, so park it and let it refill the hole afterwards.
    public void removeBlockLight(IWorldAuthority world, int x, int y, int z, int removedLevel) {
        Deque<LitCell> removal = new ArrayDeque<>();
        Deque<LitCell> relight = new ArrayDeque<>();

        setBlockLight(world, x, y, z, LightMap.MIN_LEVEL);
        removal.add(new LitCell(x, y, z, removedLevel));

        while (!removal.isEmpty()) {
            LitCell cell = removal.poll();
            for (Direction dir : Direction.DIRECTIONS) {
                int nx = cell.x() + dir.dx();
                int ny = cell.y() + dir.dy();
                int nz = cell.z() + dir.dz();
                if (!Chunk.inYRange(ny)) continue;
                int level = blockLight(world, nx, ny, nz);
                if (level == LightMap.MIN_LEVEL) continue;
                if (level < cell.level()) {
                    setBlockLight(world, nx, ny, nz, LightMap.MIN_LEVEL);
                    removal.add(new LitCell(nx, ny, nz, level));
                } else {
                    relight.add(new LitCell(nx, ny, nz, level));
                }
            }
        }

        while (!relight.isEmpty()) {
            LitCell cell = relight.poll();
            int level = blockLight(world, cell.x(), cell.y(), cell.z());
            // A level of 1 has nothing left to give a neighbour, so it can't refill anything.
            if (level <= 1) continue;
            for (Direction dir : Direction.DIRECTIONS) {
                int nx = cell.x() + dir.dx();
                int ny = cell.y() + dir.dy();
                int nz = cell.z() + dir.dz();
                if (!Chunk.inYRange(ny)) continue;
                if (world.getBlock(nx, ny, nz).opaque()) continue;
                if (blockLight(world, nx, ny, nz) >= level - 1) continue;
                setBlockLight(world, nx, ny, nz, level - 1);
                relight.add(new LitCell(nx, ny, nz, level - 1));
            }
        }
    }

    private record LitCell(int x, int y, int z, int level) {}

    // A chunk that isn't loaded, or is loaded but has never been through compute(), reads as dark
    // and swallows writes. Neither is a cell we can be wrong about: there's no mesh built from it
    // yet, and whenever one is, it comes from a full recompute.
    private int blockLight(IWorldAuthority world, int x, int y, int z) {
        Chunk chunk = chunkAt(world, x, z);
        if (chunk == null || !chunk.hasLight()) return LightMap.MIN_LEVEL;
        return chunk.getBlockLight(local(x), y, local(z));
    }

    private void setBlockLight(IWorldAuthority world, int x, int y, int z, int level) {
        Chunk chunk = chunkAt(world, x, z);
        if (chunk == null || !chunk.hasLight()) return;
        chunk.setBlockLight(local(x), y, local(z), level);
    }

    private Chunk chunkAt(IWorldAuthority world, int x, int z) {
        return world.getChunk(new ChunkPos(Math.floorDiv(x, Chunk.SIZE_XZ), Math.floorDiv(z, Chunk.SIZE_XZ)));
    }

    private static int local(int worldCoordinate) {
        return Math.floorMod(worldCoordinate, Chunk.SIZE_XZ);
    }
}
