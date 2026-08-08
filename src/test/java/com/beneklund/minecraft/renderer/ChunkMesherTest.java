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

    // No adjacent chunks loaded. Opaque blocks still emit their boundary faces; transparent
    // ones cull theirs (see isCulled), so tests that aren't about boundary policy should keep
    // their blocks off the chunk edge.
    private static ChunkMesher.ChunkWithNeighbors noNeighbors(Chunk chunk) {
        return new ChunkMesher.ChunkWithNeighbors(chunk, null, null, null, null);
    }

    // a single isolated block has no solid neighbors → all 6 faces emitted
    @Test
    void singleBlock_producesSixQuads() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        assertEquals(24, data.vertexCount(), "6 quads × 4 vertices");
        assertEquals(24 * 10, data.opaqueVerts().length, "24 vertices × 10 floats");
        assertEquals(36, data.opaqueIdx().length, "6 quads × 6 indices");
        assertEquals(0, data.transparentVerts().length, "stone is opaque — nothing in the transparent buffer");
        assertEquals(0, data.transparentIdx().length);
    }

    // a transparent block (water) routes all its geometry into the transparent buffer instead
    @Test
    void singleWaterBlock_goesToTransparentBuffer() {
        Chunk chunk = emptyChunk();
        // Interior, not (0,64,0) — on the edge two of its faces would resolve to unloaded
        // neighbors and get culled, which is a boundary question, not a routing one.
        chunk.setBlock(1, 64, 1, Block.WATER);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        assertEquals(24, data.vertexCount(), "6 quads × 4 vertices");
        assertEquals(24 * 10, data.transparentVerts().length, "water lives in the transparent buffer");
        assertEquals(36, data.transparentIdx().length);
        assertEquals(0, data.opaqueVerts().length, "nothing opaque");
        assertEquals(0, data.opaqueIdx().length);
    }

    // the shared face between two adjacent blocks is culled by both — 10 quads not 12
    @Test
    void twoAdjacentBlocks_sharedFaceCulled() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.STONE);
        chunk.setBlock(1, 64, 0, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        assertEquals(40, data.vertexCount(), "10 quads × 4 vertices");
        assertEquals(60, data.opaqueIdx().length, "10 quads × 6 indices");
    }

    // all-air chunk produces no geometry
    @Test
    void allAirChunk_producesEmptyMesh() {
        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(emptyChunk()));

        assertEquals(0, data.vertexCount());
        assertEquals(0, data.opaqueVerts().length);
        assertEquals(0, data.opaqueIdx().length);
        assertEquals(0, data.transparentVerts().length);
        assertEquals(0, data.transparentIdx().length);
    }

    // The seam rule that is MC-4 (ChunkMesher.isCulled, the resolve()-came-back-empty branch).
    // A block at (0,64,0) has two faces that leave the chunk: -x resolves to west, -z to north.
    // With neither loaded the guess splits by block type — transparent culls, opaque emits.
    @Test
    void waterOnChunkCorner_cullsTheSeamFacesAgainstUnloadedNeighbors() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.WATER);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        assertEquals(16, data.vertexCount(), "4 quads — both seam faces culled, no pane standing in the lake");
        assertEquals(24, data.transparentIdx().length, "4 quads × 6 indices");
        assertEquals(0, data.opaqueIdx().length);
    }

    @Test
    void stoneOnChunkCorner_emitsTheSeamFacesAgainstUnloadedNeighbors() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        assertEquals(24, data.vertexCount(), "6 quads — opaque emits, or you see through the render edge");
        assertEquals(36, data.opaqueIdx().length);
    }

    // A loaded neighbor is a real answer rather than a guess, so the type-based fallback
    // doesn't apply: air over the seam means the face is visible and gets emitted.
    @Test
    void waterOnChunkCorner_emitsSeamFacesWhenNeighborsAreLoadedAndEmpty() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.WATER);

        var cn = new ChunkMesher.ChunkWithNeighbors(chunk, emptyChunk(), null, null, emptyChunk());
        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), cn);

        assertEquals(24, data.vertexCount(), "6 quads — a loaded air neighbor means the face is really visible");
        assertEquals(36, data.transparentIdx().length);
    }

    // ...and matching water across the seam culls it, which is the case the whole rule exists for.
    @Test
    void waterAcrossSeam_cullsAgainstMatchingWaterInALoadedNeighbor() {
        Chunk chunk = emptyChunk();
        chunk.setBlock(0, 64, 0, Block.WATER);

        // west's adjoining column is x=15 (floorMod(-1, 16)); north stays air so only one face culls
        Chunk west = emptyChunk();
        west.setBlock(15, 64, 0, Block.WATER);
        var cn = new ChunkMesher.ChunkWithNeighbors(chunk, emptyChunk(), null, null, west);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), cn);

        assertEquals(20, data.vertexCount(), "5 quads — the west seam face culls, the north one survives");
        assertEquals(30, data.transparentIdx().length);
    }

    // interior blocks of a fully solid chunk are completely culled;
    // only the 6 outer shell surfaces are emitted
    @Test
    void solidChunk_onlySurfaceFacesEmitted() {
        Chunk chunk = emptyChunk();
        for (int x = 0; x < Chunk.SIZE_XZ; x++)
            for (int y = 0; y < Chunk.SIZE_Y; y++)
                for (int z = 0; z < Chunk.SIZE_XZ; z++) chunk.setBlock(x, y, z, Block.STONE);

        ChunkMeshData data = mesher.mesh(new ChunkPos(0, 0), noNeighbors(chunk));

        // outer shell: 2 caps (16×16 each) + 4 sides (16×256 each)
        int expectedFaces = 2 * (16 * 16) + 4 * (16 * 256); // = 16896
        assertEquals(expectedFaces * 4, data.vertexCount(), "only outer-shell faces emitted");
        assertEquals(expectedFaces * 6, data.opaqueIdx().length);
    }
}
