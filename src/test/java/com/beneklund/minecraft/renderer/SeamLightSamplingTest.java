package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.ChunkWithNeighbors;
import com.beneklund.minecraft.world.LightEngine;
import org.junit.jupiter.api.Test;

// Reproduces the in-game light probe exactly: a log on the chunk's south edge (local z=15) whose
// outward face looks into a fully lit air cell belonging to the *south neighbour*. The probe said
// that cell reads sky=15, so every vertex of every face here should come out lit.
class SeamLightSamplingTest {
    private static final int FLOATS_PER_VERTEX = 12;
    private static final int SKY_SLOT = 10;

    private final ChunkMesher mesher = new ChunkMesher(BlockRegistry.createDefault(), null);
    private final LightEngine lightEngine = new LightEngine(BlockRegistry.createDefault());

    @Test
    void faceOnTheSouthSeam_samplesTheNeighboursLight() {
        Chunk center = new Chunk();
        center.setBlock(3, 73, 15, Block.OAK_LOG);

        Chunk south = litAir();
        Chunk north = litAir();
        Chunk east = litAir();
        Chunk west = litAir();

        ChunkWithNeighbors cn = new ChunkWithNeighbors(center, north, south, east, west, null, null, null, null);
        center.setLightData(lightEngine.compute(cn));

        float[] verts = mesher.mesh(new ChunkPos(0, 0), cn).opaque().vertices();

        float darkest = 1.0f;
        for (int v = 0; v < verts.length / FLOATS_PER_VERTEX; v++) {
            darkest = Math.min(darkest, verts[v * FLOATS_PER_VERTEX + SKY_SLOT]);
        }
        // (15+15+15+14)/4/15 — one corner of one face samples the cell under the log, which sits in
        // the log's own column shadow and comes back refilled at 14. Everything else is 15. The
        // point of the test is the seam: a face reading the neighbour's LightMap as zeros would
        // land near 0 here, not near 1.
        assertEquals(59.0f / 60.0f, darkest, 1e-5f, "a face on the chunk seam must see the neighbour's light");
    }

    // An all-air chunk that has actually been through the light engine, so its LightMap holds 15
    // rather than the all-zero default a freshly constructed Chunk carries.
    private Chunk litAir() {
        Chunk chunk = new Chunk();
        chunk.setLightData(lightEngine.compute(ChunkWithNeighbors.noNeighbors(chunk)));
        return chunk;
    }
}
