package com.beneklund.minecraft.world;

import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.util.Direction;
import java.util.ArrayDeque;
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
        LightMap lightMap = new LightMap(Chunk.size());
        Chunk chunk = cn.center();
        Deque<Integer> sky = new ArrayDeque<>();
        computeSkyColumnPass(cn, lightMap, sky);
        computeAcrossChunksPass(cn, lightMap, sky, Channel.SKY);
        computePropagationPass(chunk, lightMap, sky, Channel.SKY);

        Deque<Integer> block = new ArrayDeque<>();
        computeBlocksPass(chunk, lightMap, block);
        computeAcrossChunksPass(cn, lightMap, block, Channel.BLOCK);
        computePropagationPass(chunk, lightMap, block, Channel.BLOCK);
        return lightMap;
    }

    private void computeSkyColumnPass(ChunkWithNeighbors cn, LightMap map, Deque<Integer> propagationQueue) {
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                int y = Chunk.SIZE_Y - 1;
                while (y >= 0 && !registry.get(cn.blockAt(x, y, z)).opaque()) {
                    int index = Chunk.index(x, y, z);
                    propagationQueue.add(index);
                    map.setSky(index, LightMap.MAX_LEVEL);
                    y--;
                }
            }
        }
    }

    private void computeBlocksPass(Chunk c, LightMap map, Deque<Integer> queue) {
        for (int i = 0; i < Chunk.size(); i++) {
            int level = registry.get(c.getBlock(i)).lightLevel();
            if (level > 0) {
                map.setBlock(i, level);
                queue.add(i);
            }
        }
    }

    private void computeAcrossChunksPass(
            ChunkWithNeighbors cn, LightMap map, Deque<Integer> propagationQueue, Channel channel) {
        Chunk c = cn.center();
        if (cn.resolve(0, -1).isPresent()) {
            for (int x = 0; x < Chunk.SIZE_XZ; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    int light = (channel == Channel.SKY) ? cn.skyLightAt(x, y, -1) : cn.blockLightAt(x, y, -1);
                    seedBorder(c, map, propagationQueue, channel, Chunk.index(x, y, 0), light - 1);
                }
            }
        }
        if (cn.resolve(-1, 0).isPresent()) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    int light = (channel == Channel.SKY) ? cn.skyLightAt(-1, y, z) : cn.blockLightAt(-1, y, z);
                    seedBorder(c, map, propagationQueue, channel, Chunk.index(0, y, z), light - 1);
                }
            }
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
}
