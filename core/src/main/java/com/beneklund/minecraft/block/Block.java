package com.beneklund.minecraft.block;

// A block type. The enum is the readable currency passed around the world API; the byte `id`
// is the compact storage form that Chunk packs into its byte[] (1 byte/block keeps 65k-block
// chunks at 64 KiB and cache-friendly). Convert with id() and fromId().
public enum Block {
    AIR((byte) 0),
    STONE((byte) 1),
    DIRT((byte) 2),
    GRASS((byte) 3),
    BEDROCK((byte) 4),
    SAND((byte) 5),
    GRAVEL((byte) 6),
    OAK_LOG((byte) 7),
    OAK_LEAF((byte) 8),
    WATER((byte) 9),
    COBBLE((byte) 10),
    GLASS((byte) 11),
    OAK_PLANK((byte) 12),
    COAL_ORE((byte) 13),
    IRON_ORE((byte) 14),
    SNOW((byte) 15),
    LAVA((byte) 16),
    SANDSTONE((byte) 17),
    RED_SAND((byte) 18),
    CLAY((byte) 19),
    OBSIDIAN((byte) 20),
    ICE((byte) 21),
    PACKED_ICE((byte) 22),
    ANDESITE((byte) 23),
    DIORITE((byte) 24),
    GRANITE((byte) 25),
    GOLD_ORE((byte) 26),
    DIAMOND_ORE((byte) 27),
    REDSTONE_ORE((byte) 28),
    LAPIS_ORE((byte) 29),
    EMERALD_ORE((byte) 30),
    MOSSY_COBBLE((byte) 31),
    STONE_BRICK((byte) 32),
    BRICKS((byte) 33),
    BIRCH_LOG((byte) 34),
    BIRCH_LEAF((byte) 35),
    BIRCH_PLANK((byte) 36),
    SPRUCE_LOG((byte) 37),
    SPRUCE_LEAF((byte) 38),
    SPRUCE_PLANK((byte) 39),
    JUNGLE_LOG((byte) 40),
    JUNGLE_LEAF((byte) 41),
    JUNGLE_PLANK((byte) 42),
    ACACIA_LOG((byte) 43),
    ACACIA_LEAF((byte) 44),
    ACACIA_PLANK((byte) 45),
    DARK_OAK_LOG((byte) 46),
    DARK_OAK_LEAF((byte) 47),
    DARK_OAK_PLANK((byte) 48),
    COAL_BLOCK((byte) 49),
    IRON_BLOCK((byte) 50),
    GOLD_BLOCK((byte) 51),
    DIAMOND_BLOCK((byte) 52),
    GLOWSTONE((byte) 53);

    private final byte id;

    Block(byte id) {
        this.id = id;
    }

    // The storage byte that goes into Chunk's byte[].
    public byte id() {
        return id;
    }

    // Reverse lookup, byte -> Block. Indexed by id for O(1) access on the meshing hot path.
    // Unknown ids fall back to AIR so a stray storage byte renders invisible rather than NPE-ing
    // on a worker thread (mirrors BlockRegistry's defensive default).
    private static final Block[] BY_ID = new Block[256];

    static {
        for (Block b : values()) BY_ID[b.id & 0xFF] = b;
    }

    public static Block fromId(byte id) {
        Block b = BY_ID[id & 0xFF];
        return b == null ? AIR : b;
    }
}
