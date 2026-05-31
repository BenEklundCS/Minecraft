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
                                true,
                                false,
                                "snow_top",
                                "snow_top",
                                "snow_side",
                                "snow_side",
                                "snow_side",
                                "snow_side"))));
    }
}
