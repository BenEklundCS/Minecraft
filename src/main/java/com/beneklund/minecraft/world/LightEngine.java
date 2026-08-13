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

    public LightMap compute(ChunkWithNeighbors cn) {
        LightMap lightMap = new LightMap(Chunk.size());
        Chunk chunk = cn.center();
        Deque<Integer> queue = new ArrayDeque<>();
        computeSkyColumnPass(cn, lightMap, queue);
        computeAcrossChunksPass(cn, lightMap, queue);
        computePropagationPass(chunk, lightMap, queue);
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

    private void computeAcrossChunksPass(ChunkWithNeighbors cn, LightMap map, Deque<Integer> propagationQueue) {
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                int light = cn.skyLightAt(x, y, -1);
                int next = light - 1;
                int i = Chunk.index(x, y, 0);
                propagateAndSet(i, next, propagationQueue, map);
            }
        }
        for (int z = 0; z < Chunk.SIZE_XZ; z++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                int light = cn.skyLightAt(-1, y, z);
                int next = light - 1;
                int i = Chunk.index(0, y, z);
                propagateAndSet(i, next, propagationQueue, map);
            }
        }
    }

    private void computePropagationPass(Chunk c, LightMap map, Deque<Integer> propagationQueue) {
        while (!propagationQueue.isEmpty()) {
            int i = propagationQueue.poll();
            int light = map.sky(i);

            for (Direction dir : Direction.DIRECTIONS) {
                int ni = Chunk.neighborIndex(i, dir);
                if (ni < 0) continue;
                if (registry.get(c.getBlock(ni)).opaque()) continue;
                int next = (dir == Direction.DOWN && light == LightMap.MAX_LEVEL) ? LightMap.MAX_LEVEL : light - 1;
                propagateAndSet(ni, next, propagationQueue, map);
            }
        }
    }

    private void propagateAndSet(int index, int next, Deque<Integer> queue, LightMap map) {
        if (next > map.sky(index)) {
            map.setSky(index, next);
            queue.add(index);
        }
    }
}
