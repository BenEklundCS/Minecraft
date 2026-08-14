package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class LightRemovalReproTest {
    private static final int Y = 64;

    private final BlockRegistry registry = BlockRegistry.createDefault();
    private final LightEngine engine = new LightEngine(registry);

    @Test
    void removingGlowstone_clearsBlockLightWhenThereIsNoNeighbour() {
        Chunk a = new Chunk();
        a.setBlock(8, Y, 8, Block.GLOWSTONE);
        a.setLightData(engine.compute(ChunkWithNeighbors.noNeighbors(a)));
        assertEquals(15, a.getBlockLight(8, Y, 8), "glowstone lit its own cell");

        a.setBlock(8, Y, 8, Block.AIR);
        LightMap after = engine.compute(ChunkWithNeighbors.noNeighbors(a));

        assertEquals(0, brightestBlockLevel(after), "no emitter anywhere, so no block light anywhere");
    }

    // The bug. The glowstone sits at x=8, well inside chunk (0,0), but its light reaches eight
    // blocks into chunk (1,0) and is baked into that chunk's LightMap. Breaking it doesn't dirty
    // (1,0) — the edit isn't in it — so before the removal walk existed, (1,0) kept the glow and
    // seedSeam fed it back into (0,0) on the next remesh, one level down and there for good.
    @Test
    void removingGlowstone_clearsBlockLightInTheNeighbourItLit() {
        World world = new World(new ConcurrentHashMap<>());
        LocalWorldAuthority authority = new LocalWorldAuthority(world, registry, engine);
        ChunkPos posA = new ChunkPos(0, 0);
        ChunkPos posB = new ChunkPos(1, 0);
        Chunk a = new Chunk();
        Chunk b = new Chunk();
        world.addChunk(posA, a);
        world.addChunk(posB, b);

        authority.setBlock(8, Y, 8, Block.GLOWSTONE);
        light(world, posA);
        light(world, posB);
        assertEquals(7, b.getBlockLight(0, Y, 8), "b's seam cell is eight steps from the glowstone");

        authority.setBlock(8, Y, 8, Block.AIR);

        assertEquals(0, brightestStoredBlockLevel(b), "the neighbour gave the light up");
        assertEquals(
                0,
                brightestBlockLevel(engine.compute(ChunkWithNeighbors.around(posA, world::getChunk))),
                "so a remesh of the edited chunk has nothing to read back over the seam");
    }

    // A second emitter still standing must survive its neighbour's removal walk, including the
    // cells the two of them lit in common.
    @Test
    void removingOneGlowstone_leavesTheLightOfAnotherIntact() {
        World world = new World(new ConcurrentHashMap<>());
        LocalWorldAuthority authority = new LocalWorldAuthority(world, registry, engine);
        ChunkPos pos = new ChunkPos(0, 0);
        world.addChunk(pos, new Chunk());

        authority.setBlock(4, Y, 8, Block.GLOWSTONE);
        authority.setBlock(12, Y, 8, Block.GLOWSTONE);
        light(world, pos);

        authority.setBlock(4, Y, 8, Block.AIR);

        Chunk chunk = world.getChunk(pos);
        assertEquals(15, chunk.getBlockLight(12, Y, 8), "the surviving emitter is untouched");
        assertEquals(11, chunk.getBlockLight(8, Y, 8), "the cell between them is relit from the survivor");
        // Not dark: the survivor is eight blocks away, so the hole refills to exactly its falloff.
        assertEquals(7, chunk.getBlockLight(4, Y, 8), "the removed emitter's cell keeps only borrowed light");
        assertEquals(0, chunk.getBlockLight(0, Y, 0), "and nothing beyond the survivor's reach stays lit");
    }

    // What ChunkManager's mesh job does, minus the meshing.
    private void light(World world, ChunkPos pos) {
        world.getChunk(pos).setLightData(engine.compute(ChunkWithNeighbors.around(pos, world::getChunk)));
    }

    private static int brightestBlockLevel(LightMap map) {
        int max = 0;
        for (int i = 0; i < map.size(); i++) max = Math.max(max, map.block(i));
        return max;
    }

    private static int brightestStoredBlockLevel(Chunk chunk) {
        int max = 0;
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) max = Math.max(max, chunk.getBlockLight(x, y, z));
            }
        }
        return max;
    }
}
