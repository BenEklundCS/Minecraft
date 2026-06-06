package com.beneklund.minecraft.block;

import java.util.Map;

public class BlockRegistry {
    private final Map<Byte, BlockDef> blockDefs;

    public BlockRegistry(Map<Byte, BlockDef> blockDefs) {
        this.blockDefs = blockDefs;
    }

    // Falls back to AIR so an unregistered block ID renders as invisible rather than NPE-ing
    // in a worker thread where the stack trace would be hard to trace back to the bad ID.
    public BlockDef get(byte b) {
        return blockDefs.getOrDefault(b, blockDefs.get(Block.AIR));
    }

    public static BlockRegistry createDefault() {
        // face order: up, down, north, south, east, west
        return new BlockRegistry(Map.ofEntries(
                Map.entry(
                        Block.AIR, new BlockDef(false, true, new String[] {"air", "air", "air", "air", "air", "air"})),
                Map.entry(
                        Block.STONE, BlockDef.build(true, false, "stone", "stone", "stone", "stone", "stone", "stone")),
                Map.entry(Block.DIRT, BlockDef.build(true, false, "dirt", "dirt", "dirt", "dirt", "dirt", "dirt")),
                Map.entry(
                        Block.GRASS,
                        BlockDef.build(
                                true,
                                false,
                                "grass_top",
                                "dirt",
                                "grass_side",
                                "grass_side",
                                "grass_side",
                                "grass_side")),
                Map.entry(
                        Block.BEDROCK,
                        BlockDef.build(true, false, "bedrock", "bedrock", "bedrock", "bedrock", "bedrock", "bedrock")),
                Map.entry(Block.SAND, BlockDef.build(true, false, "sand", "sand", "sand", "sand", "sand", "sand")),
                Map.entry(
                        Block.GRAVEL,
                        BlockDef.build(true, false, "gravel", "gravel", "gravel", "gravel", "gravel", "gravel")),
                Map.entry(
                        Block.OAK_LOG,
                        BlockDef.build(
                                true,
                                false,
                                "oak_log_top",
                                "oak_log_top",
                                "oak_log_side",
                                "oak_log_side",
                                "oak_log_side",
                                "oak_log_side")),
                // leaves are non-solid so face culling against them still emits the neighbour face
                Map.entry(
                        Block.OAK_LEAF,
                        BlockDef.build(
                                false,
                                true,
                                "oak_leaves",
                                "oak_leaves",
                                "oak_leaves",
                                "oak_leaves",
                                "oak_leaves",
                                "oak_leaves")),
                Map.entry(
                        Block.WATER,
                        BlockDef.build(
                                false,
                                true,
                                "water_still",
                                "water_still",
                                "water_flowing",
                                "water_flowing",
                                "water_flowing",
                                "water_flowing")),
                Map.entry(
                        Block.COBBLE,
                        BlockDef.build(
                                true,
                                false,
                                "cobblestone",
                                "cobblestone",
                                "cobblestone",
                                "cobblestone",
                                "cobblestone",
                                "cobblestone")),
                Map.entry(
                        Block.GLASS, BlockDef.build(false, true, "glass", "glass", "glass", "glass", "glass", "glass")),
                Map.entry(
                        Block.OAK_PLANK,
                        BlockDef.build(
                                true,
                                false,
                                "oak_planks",
                                "oak_planks",
                                "oak_planks",
                                "oak_planks",
                                "oak_planks",
                                "oak_planks")),
                Map.entry(
                        Block.COAL_ORE,
                        BlockDef.build(
                                true, false, "coal_ore", "coal_ore", "coal_ore", "coal_ore", "coal_ore", "coal_ore")),
                Map.entry(
                        Block.IRON_ORE,
                        BlockDef.build(
                                true, false, "iron_ore", "iron_ore", "iron_ore", "iron_ore", "iron_ore", "iron_ore")),
                Map.entry(
                        Block.SNOW,
                        BlockDef.build(
                                true, false, "snow_top", "snow_top", "snow_side", "snow_side", "snow_side",
                                "snow_side")),
                Map.entry(
                        Block.LAVA,
                        BlockDef.build(
                                false, true, "lava_still", "lava_still", "lava_flowing", "lava_flowing",
                                "lava_flowing", "lava_flowing")),
                Map.entry(
                        Block.SANDSTONE,
                        BlockDef.build(
                                true, false, "sandstone_top", "sandstone_bottom", "sandstone_side",
                                "sandstone_side", "sandstone_side", "sandstone_side")),
                Map.entry(
                        Block.RED_SAND,
                        BlockDef.build(true, false, "red_sand", "red_sand", "red_sand", "red_sand", "red_sand",
                                "red_sand")),
                Map.entry(
                        Block.CLAY,
                        BlockDef.build(true, false, "clay", "clay", "clay", "clay", "clay", "clay")),
                Map.entry(
                        Block.OBSIDIAN,
                        BlockDef.build(true, false, "obsidian", "obsidian", "obsidian", "obsidian", "obsidian",
                                "obsidian")),
                Map.entry(
                        Block.ICE,
                        BlockDef.build(false, true, "ice", "ice", "ice", "ice", "ice", "ice")),
                Map.entry(
                        Block.PACKED_ICE,
                        BlockDef.build(true, false, "packed_ice", "packed_ice", "packed_ice", "packed_ice",
                                "packed_ice", "packed_ice")),
                Map.entry(
                        Block.ANDESITE,
                        BlockDef.build(true, false, "andesite", "andesite", "andesite", "andesite", "andesite",
                                "andesite")),
                Map.entry(
                        Block.DIORITE,
                        BlockDef.build(true, false, "diorite", "diorite", "diorite", "diorite", "diorite",
                                "diorite")),
                Map.entry(
                        Block.GRANITE,
                        BlockDef.build(true, false, "granite", "granite", "granite", "granite", "granite",
                                "granite")),
                Map.entry(
                        Block.GOLD_ORE,
                        BlockDef.build(true, false, "gold_ore", "gold_ore", "gold_ore", "gold_ore", "gold_ore",
                                "gold_ore")),
                Map.entry(
                        Block.DIAMOND_ORE,
                        BlockDef.build(true, false, "diamond_ore", "diamond_ore", "diamond_ore", "diamond_ore",
                                "diamond_ore", "diamond_ore")),
                Map.entry(
                        Block.REDSTONE_ORE,
                        BlockDef.build(true, false, "redstone_ore", "redstone_ore", "redstone_ore",
                                "redstone_ore", "redstone_ore", "redstone_ore")),
                Map.entry(
                        Block.LAPIS_ORE,
                        BlockDef.build(true, false, "lapis_ore", "lapis_ore", "lapis_ore", "lapis_ore",
                                "lapis_ore", "lapis_ore")),
                Map.entry(
                        Block.EMERALD_ORE,
                        BlockDef.build(true, false, "emerald_ore", "emerald_ore", "emerald_ore", "emerald_ore",
                                "emerald_ore", "emerald_ore")),
                Map.entry(
                        Block.MOSSY_COBBLE,
                        BlockDef.build(true, false, "mossy_cobble", "mossy_cobble", "mossy_cobble",
                                "mossy_cobble", "mossy_cobble", "mossy_cobble")),
                Map.entry(
                        Block.STONE_BRICK,
                        BlockDef.build(true, false, "stone_bricks", "stone_bricks", "stone_bricks",
                                "stone_bricks", "stone_bricks", "stone_bricks")),
                Map.entry(
                        Block.BRICKS,
                        BlockDef.build(true, false, "bricks", "bricks", "bricks", "bricks", "bricks", "bricks")),
                Map.entry(
                        Block.BIRCH_LOG,
                        BlockDef.build(true, false, "birch_log_top", "birch_log_top", "birch_log_side",
                                "birch_log_side", "birch_log_side", "birch_log_side")),
                Map.entry(
                        Block.BIRCH_LEAF,
                        BlockDef.build(false, true, "birch_leaves", "birch_leaves", "birch_leaves",
                                "birch_leaves", "birch_leaves", "birch_leaves")),
                Map.entry(
                        Block.BIRCH_PLANK,
                        BlockDef.build(true, false, "birch_planks", "birch_planks", "birch_planks",
                                "birch_planks", "birch_planks", "birch_planks")),
                Map.entry(
                        Block.SPRUCE_LOG,
                        BlockDef.build(true, false, "spruce_log_top", "spruce_log_top", "spruce_log_side",
                                "spruce_log_side", "spruce_log_side", "spruce_log_side")),
                Map.entry(
                        Block.SPRUCE_LEAF,
                        BlockDef.build(false, true, "spruce_leaves", "spruce_leaves", "spruce_leaves",
                                "spruce_leaves", "spruce_leaves", "spruce_leaves")),
                Map.entry(
                        Block.SPRUCE_PLANK,
                        BlockDef.build(true, false, "spruce_planks", "spruce_planks", "spruce_planks",
                                "spruce_planks", "spruce_planks", "spruce_planks")),
                Map.entry(
                        Block.JUNGLE_LOG,
                        BlockDef.build(true, false, "jungle_log_top", "jungle_log_top", "jungle_log_side",
                                "jungle_log_side", "jungle_log_side", "jungle_log_side")),
                Map.entry(
                        Block.JUNGLE_LEAF,
                        BlockDef.build(false, true, "jungle_leaves", "jungle_leaves", "jungle_leaves",
                                "jungle_leaves", "jungle_leaves", "jungle_leaves")),
                Map.entry(
                        Block.JUNGLE_PLANK,
                        BlockDef.build(true, false, "jungle_planks", "jungle_planks", "jungle_planks",
                                "jungle_planks", "jungle_planks", "jungle_planks")),
                Map.entry(
                        Block.ACACIA_LOG,
                        BlockDef.build(true, false, "acacia_log_top", "acacia_log_top", "acacia_log_side",
                                "acacia_log_side", "acacia_log_side", "acacia_log_side")),
                Map.entry(
                        Block.ACACIA_LEAF,
                        BlockDef.build(false, true, "acacia_leaves", "acacia_leaves", "acacia_leaves",
                                "acacia_leaves", "acacia_leaves", "acacia_leaves")),
                Map.entry(
                        Block.ACACIA_PLANK,
                        BlockDef.build(true, false, "acacia_planks", "acacia_planks", "acacia_planks",
                                "acacia_planks", "acacia_planks", "acacia_planks")),
                Map.entry(
                        Block.DARK_OAK_LOG,
                        BlockDef.build(true, false, "dark_oak_log_top", "dark_oak_log_top", "dark_oak_log_side",
                                "dark_oak_log_side", "dark_oak_log_side", "dark_oak_log_side")),
                Map.entry(
                        Block.DARK_OAK_LEAF,
                        BlockDef.build(false, true, "dark_oak_leaves", "dark_oak_leaves", "dark_oak_leaves",
                                "dark_oak_leaves", "dark_oak_leaves", "dark_oak_leaves")),
                Map.entry(
                        Block.DARK_OAK_PLANK,
                        BlockDef.build(true, false, "dark_oak_planks", "dark_oak_planks", "dark_oak_planks",
                                "dark_oak_planks", "dark_oak_planks", "dark_oak_planks")),
                Map.entry(
                        Block.COAL_BLOCK,
                        BlockDef.build(true, false, "coal_block", "coal_block", "coal_block", "coal_block",
                                "coal_block", "coal_block")),
                Map.entry(
                        Block.IRON_BLOCK,
                        BlockDef.build(true, false, "iron_block", "iron_block", "iron_block", "iron_block",
                                "iron_block", "iron_block")),
                Map.entry(
                        Block.GOLD_BLOCK,
                        BlockDef.build(true, false, "gold_block", "gold_block", "gold_block", "gold_block",
                                "gold_block", "gold_block")),
                Map.entry(
                        Block.DIAMOND_BLOCK,
                        BlockDef.build(true, false, "diamond_block", "diamond_block", "diamond_block",
                                "diamond_block", "diamond_block", "diamond_block"))));
    }
}
