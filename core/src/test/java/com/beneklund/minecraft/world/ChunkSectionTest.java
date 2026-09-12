package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import org.junit.jupiter.api.Test;

public class ChunkSectionTest {
    @Test
    void section_staysUnallocatedUntilANonAirWrite() {
        ChunkSection s = new ChunkSection();
        assertTrue(s.isEmpty());
        s.set(0, Block.AIR.id());
        assertTrue(s.isEmpty()); // writing air must not allocate
        assertNull(s.getBlocks()); // package-private accessor

        s.set(0, Block.STONE.id());
        assertFalse(s.isEmpty());
        assertNotNull(s.getBlocks());
    }

    @Test
    void section_clearingBackToAirReportsEmpty() {
        ChunkSection s = new ChunkSection();
        s.set(5, Block.STONE.id());
        s.set(5, Block.AIR.id());
        assertTrue(s.isEmpty()); // count went back down
    }
}
