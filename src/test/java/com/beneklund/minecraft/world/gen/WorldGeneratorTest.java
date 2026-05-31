package com.beneklund.minecraft.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    private final WorldGenerator generator = new WorldGenerator(BlockRegistry.createDefault());
    private final ChunkPos origin = new ChunkPos(0, 0);
    private final long seed = 42L;

    // same inputs always produce identical block data — generator has no mutable state
    @Test
    void generate_sameInputsTwice_identicalChunks() {
        Chunk a = new Chunk(); generator.generate(origin, seed, a);
        Chunk b = new Chunk(); generator.generate(origin, seed, b);
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    assertEquals(
                            a.getBlock(x, y, z),
                            b.getBlock(x, y, z),
                            "block mismatch at (" + x + "," + y + "," + z + ")");
                }
            }
        }
    }

    // y=0 is always bedrock — no biome or noise value can override it
    @Test
    void generate_bedrock_atYZeroForAllColumns() {
        Chunk chunk = new Chunk(); generator.generate(origin, seed, chunk);
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                assertEquals(Block.BEDROCK, chunk.getBlock(x, 0, z), "expected BEDROCK at (" + x + ",0," + z + ")");
            }
        }
    }

    // surfaceY is clamped to 250; tree headroom guard caps canopy at y=254;
    // so y=255 (HEIGHT-1) must always be AIR
    @Test
    void generate_topLayer_isAlwaysAir() {
        Chunk chunk = new Chunk(); generator.generate(origin, seed, chunk);
        int top = Chunk.SIZE_Y - 1;
        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                assertEquals(Block.AIR, chunk.getBlock(x, top, z), "expected AIR at (" + x + "," + top + "," + z + ")");
            }
        }
    }
}
