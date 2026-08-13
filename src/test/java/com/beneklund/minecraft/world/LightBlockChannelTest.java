package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import org.junit.jupiter.api.Test;

// The block channel, which differs from sky in exactly three ways: it is seeded from emitting
// blocks rather than open columns, it has no downward exception, and it lives in the low nibble.
// A glowstone lights a sphere, not a shaft — that is what the straight-down assertions pin.
class LightBlockChannelTest {
    private final LightEngine engine = new LightEngine(BlockRegistry.createDefault());

    @Test
    void glowstone_lightsASphereOneLevelPerStep() {
        Chunk chunk = new Chunk();
        chunk.setBlock(8, 64, 8, Block.GLOWSTONE);

        LightMap map = lightOf(chunk);

        assertEquals(15, blockAt(map, 8, 64, 8), "the emitter's own cell");

        // all six neighbours are one step out, including up and down
        assertEquals(14, blockAt(map, 9, 64, 8));
        assertEquals(14, blockAt(map, 7, 64, 8));
        assertEquals(14, blockAt(map, 8, 64, 9));
        assertEquals(14, blockAt(map, 8, 64, 7));
        assertEquals(14, blockAt(map, 8, 65, 8));
        assertEquals(14, blockAt(map, 8, 63, 8));

        assertEquals(13, blockAt(map, 10, 64, 8), "two steps out");
    }

    // The assertion the whole channel split exists for: with the downward exception off, four
    // steps down costs four levels. If this reads 15 the exception is keyed on the direction
    // rather than on the channel.
    @Test
    void blockLightDoesNotFallFreely() {
        Chunk chunk = new Chunk();
        chunk.setBlock(8, 64, 8, Block.GLOWSTONE);

        LightMap map = lightOf(chunk);

        assertEquals(11, blockAt(map, 8, 60, 8));
    }

    // An empty chunk with no emitter has nothing to seed the block channel, so every cell is
    // dark in that nibble however bright the sky above it is.
    @Test
    void noEmitter_leavesBlockChannelDark() {
        LightMap map = lightOf(new Chunk());

        assertEquals(LightMap.MIN_LEVEL, blockAt(map, 8, 64, 8));
        assertEquals(LightMap.MIN_LEVEL, blockAt(map, 0, 64, 0));
        assertEquals(LightMap.MIN_LEVEL, blockAt(map, 15, 200, 15));
    }

    // Sky is scaled by time of day and block light is not, which is the entire reason they are
    // two nibbles. An emitter must not raise the sky channel anywhere.
    @Test
    void emitterDoesNotWriteIntoTheSkyChannel() {
        Chunk chunk = new Chunk();
        chunk.setBlock(8, 64, 8, Block.GLOWSTONE);

        LightMap map = lightOf(chunk);

        // the glowstone is opaque, so it stops its own column and the sky under it is whatever
        // floods back in from the open columns beside it — never boosted by the emitter
        assertEquals(LightMap.MIN_LEVEL, skyAt(map, 8, 64, 8));
        assertEquals(LightMap.MAX_LEVEL - 1, skyAt(map, 8, 63, 8));
    }

    private LightMap lightOf(Chunk chunk) {
        return engine.compute(ChunkWithNeighbors.noNeighbors(chunk));
    }

    private int blockAt(LightMap map, int x, int y, int z) {
        return map.block(Chunk.index(x, y, z));
    }

    private int skyAt(LightMap map, int x, int y, int z) {
        return map.sky(Chunk.index(x, y, z));
    }
}
