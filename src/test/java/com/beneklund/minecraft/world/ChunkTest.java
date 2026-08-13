package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import org.junit.jupiter.api.Test;

class ChunkTest {
    private Chunk emptyChunk() {
        return new Chunk();
    }

    @Test
    void setBlock_thenGetBlock_returnsStoredId() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(3, 64, 5, Block.STONE);
        assertEquals(Block.STONE, chunk.getBlock(3, 64, 5));
    }

    @Test
    void getBlock_freshChunk_isAir() {
        Chunk chunk = emptyChunk();
        assertEquals(Block.AIR, chunk.getBlock(0, 0, 0));
    }

    // the max corner (15,255,15) maps to the last array slot - no overflow
    @Test
    void index_maxCorner_doesNotOverflow() {
        Chunk chunk = emptyChunk();
        assertDoesNotThrow(() -> chunk.setBlock(15, 255, 15, Block.STONE));
        assertEquals(Block.STONE, chunk.getBlock(15, 255, 15));
    }
}
