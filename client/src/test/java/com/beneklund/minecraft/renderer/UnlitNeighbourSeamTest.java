package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.ChunkWithNeighbors;
import com.beneklund.minecraft.world.LightEngine;
import org.junit.jupiter.api.Test;

// The black tree face, reduced. A log stands on the chunk's east edge with open sky above it and
// open air to the east, so its east face should be fully lit. The east neighbour has blocks but has
// not been through the light engine yet — the state ChunkManager.meshable() admits between
// QUEUED_MESH and the end of that chunk's own mesh job — and a fresh Chunk carries an all-zero
// LightMap. The mesher samples the neighbour's LightMap directly for any face on the seam, so it
// reads that "not computed yet" as "pitch dark".
class UnlitNeighbourSeamTest {
    private static final int FLOATS_PER_VERTEX = 12;
    private static final int X_SLOT = 0;
    private static final int SKY_SLOT = 10;

    private static final int LOG_X = Chunk.SIZE_XZ - 1;
    private static final int LOG_Y = 73;
    private static final int LOG_Z = 8;

    private final BlockRegistry registry = BlockRegistry.createDefault();
    private final ChunkMesher mesher = new ChunkMesher(registry, null);
    private final LightEngine lightEngine = new LightEngine(registry);

    // Stated as a comparison rather than an absolute level on purpose: the point isn't what the seam
    // face's light is, it's that meshing order can't change it.
    @Test
    void seamLightIsTheSameWhetherOrNotTheNeighbourHasBeenLitYet() {
        float lit = darkestVertexOnTheEastSeam(litAir());
        float notLitYet = darkestVertexOnTheEastSeam(new Chunk());
        System.out.println("seam light: neighbour lit=" + lit + ", neighbour not lit yet=" + notLitYet);
        assertEquals(lit, notLitYet, 1e-5f, "a neighbour that has not been lit yet must not read as darkness");
    }

    // Meshes a center chunk holding one edge log and answers the dimmest sky value on the face that
    // looks into the east neighbour. Everything but the east neighbour is lit air, so any darkness
    // here came across the east seam.
    private float darkestVertexOnTheEastSeam(Chunk east) {
        Chunk center = new Chunk();
        center.setBlock(LOG_X, LOG_Y, LOG_Z, Block.OAK_LOG);

        ChunkWithNeighbors cn =
                new ChunkWithNeighbors(center, litAir(), litAir(), east, litAir(), null, null, null, null);
        center.setLightData(lightEngine.compute(cn));

        float[] verts = mesher.mesh(new ChunkPos(0, 0), cn).opaque().vertices();
        float darkest = Float.MAX_VALUE;
        for (int v = 0; v < verts.length / FLOATS_PER_VERTEX; v++) {
            int base = v * FLOATS_PER_VERTEX;
            // The east face is the quad sitting on the x = 16 plane — the seam itself.
            if (verts[base + X_SLOT] != Chunk.SIZE_XZ) continue;
            darkest = Math.min(darkest, verts[base + SKY_SLOT]);
        }
        return darkest;
    }

    // An all-air chunk that has actually been through the light engine, so its LightMap holds 15
    // rather than the all-zero default a freshly constructed Chunk carries.
    private Chunk litAir() {
        Chunk chunk = new Chunk();
        chunk.setLightData(lightEngine.compute(ChunkWithNeighbors.noNeighbors(chunk)));
        return chunk;
    }
}
