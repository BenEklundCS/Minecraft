package com.beneklund.minecraft.block;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BlockRegistryTest {
    private final BlockRegistry registry = BlockRegistry.createDefault();

    @Test
    void air_isNotSolid() {
        assertFalse(registry.get(Block.AIR).solid());
    }

    @Test
    void stone_isSolid() {
        assertTrue(registry.get(Block.STONE).solid());
    }

    @Test
    void glass_isTransparent() {
        assertTrue(registry.get(Block.GLASS).transparent());
    }

    @Test
    void oakLeaf_isTransparent() {
        assertTrue(registry.get(Block.OAK_LEAF).transparent());
    }

    @Test
    void allBlockIds_returnNonNullBlockDef() {
        byte[] allIds = {
            Block.AIR, Block.STONE, Block.DIRT, Block.GRASS, Block.BEDROCK,
            Block.SAND, Block.GRAVEL, Block.OAK_LOG, Block.OAK_LEAF, Block.WATER,
            Block.COBBLE, Block.GLASS, Block.OAK_PLANK, Block.COAL_ORE,
            Block.IRON_ORE, Block.SNOW
        };
        for (byte id : allIds) {
            assertNotNull(registry.get(id), "BlockDef was null for block id " + id);
        }
    }
}
