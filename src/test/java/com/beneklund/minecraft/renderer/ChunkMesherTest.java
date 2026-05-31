package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import org.junit.jupiter.api.Test;

class ChunkMesherTest {

    // null atlas → getUVs falls back to DEFAULT_UV; no GL context needed in tests
    private final ChunkMesher mesher = new ChunkMesher(BlockRegistry.createDefault(), null);

    private Chunk emptyChunk() {
        return new Chunk();
    }

    // a single isolated block has no solid neighbors → all 6 faces emitted
    @Test
    void singleBlock_producesSixQuads() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), chunk);

        assertEquals(24, data.vertexCount(), "6 quads × 4 vertices");
        assertEquals(24 * 10, data.vertices().length, "24 vertices × 10 floats");
        assertEquals(36, data.indices().length, "6 quads × 6 indices");
    }

    // the shared face between two adjacent blocks is culled by both — 10 quads not 12
    @Test
    void twoAdjacentBlocks_sharedFaceCulled() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.STONE);
        chunk.setBlock(1, 64, 0, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), chunk);

        assertEquals(40, data.vertexCount(), "10 quads × 4 vertices");
        assertEquals(60, data.indices().length, "10 quads × 6 indices");
    }

    // all-air chunk produces no geometry
    @Test
    void allAirChunk_producesEmptyMesh() {
        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), emptyChunk());

        assertEquals(0, data.vertexCount());
        assertEquals(0, data.vertices().length);
        assertEquals(0, data.indices().length);
    }

    // interior blocks of a fully solid chunk are completely culled;
    // only the 6 outer shell surfaces are emitted
    @Test
    void solidChunk_onlySurfaceFacesEmitted() {
        Chunk chunk = emptyChunk();
        for (int x = 0; x < Chunk.SIZE_XZ; x++)
            for (int y = 0; y < Chunk.SIZE_Y; y++)
                for (int z = 0; z < Chunk.SIZE_XZ; z++) chunk.setBlock(x, y, z, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), chunk);

        // outer shell: 2 caps (16×16 each) + 4 sides (16×256 each)
        int expectedFaces = 2 * (16 * 16) + 4 * (16 * 256); // = 16896
        assertEquals(expectedFaces * 4, data.vertexCount(), "only outer-shell faces emitted");
        assertEquals(expectedFaces * 6, data.indices().length);
    }
}
