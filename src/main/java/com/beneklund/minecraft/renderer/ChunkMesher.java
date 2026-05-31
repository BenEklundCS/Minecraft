package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.util.Direction;
import com.beneklund.minecraft.world.Chunk;
import java.util.ArrayList;
import java.util.List;

// Converts a Chunk's block data into a ChunkMeshData (float[] vertices, int[] indices).
// No GL calls — safe to run on any worker thread. The caller uploads the result to the
// GPU via GpuMesh on the main thread.
//
// Vertex format (10 floats, stride 40 bytes):
//   [0-2]  x, y, z       world position
//   [3-4]  u, v          atlas UV
//   [5]    ao            ambient occlusion (placeholder 1.0 until Phase 20)
//   [6]    faceId        0=UP, 1=side, 2=DOWN (drives brightness bands in chunk.frag)
//   [7-9]  r, g, b       biome tint (1,1,1 = no tint)
public class ChunkMesher {

    // 4 corner offsets per face, CCW winding when viewed from outside the block.
    // Indexed by Direction.ordinal(): UP=0, DOWN=1, NORTH=2, SOUTH=3, EAST=4, WEST=5.
    // Each row is one face; each entry is (dx, dy, dz) added to the block's origin.
    private static final float[][][] FACE_VERTICES = {
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // UP    — y+1 surface
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // DOWN  — y=0 surface
        {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}, // NORTH — z=0 surface
        {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, // SOUTH — z+1 surface
        {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}, // EAST  — x+1 surface
        {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, // WEST  — x=0 surface
    };

    // Block-space step to reach the neighbor in each direction.
    private static final int[][] NEIGHBOR_OFFSETS = {
        {0, 1, 0}, // UP
        {0, -1, 0}, // DOWN
        {0, 0, -1}, // NORTH
        {0, 0, 1}, // SOUTH
        {1, 0, 0}, // EAST
        {-1, 0, 0}, // WEST
    };

    // faceId values matched to the brightness bands in chunk.frag:
    //   < 0.5 → 1.0 (bright),  < 2.5 → 0.8 (side),  else → 0.6 (dark)
    private static final float FACE_ID_UP = 0.0f;
    private static final float FACE_ID_SIDE = 1.0f;
    private static final float FACE_ID_DOWN = 2.0f;

    private static final float[] DEFAULT_UV = {0f, 0f, 1f, 1f};

    // Per-face UV fractions: [u0,v0, u1,v1, u2,v2, u3,v3] where 0=uMin/vMin, 1=uMax/vMax.
    // Index order matches FACE_VERTICES — vertex 0 uses fracs[0,1], vertex 1 uses [2,3], etc.
    //
    // The V-axis is flipped between STB and OpenGL:
    //   STB loads images top-to-bottom  → row 0 = top of the image file
    //   OpenGL UV V=0                   → bottom of the texture on screen
    // So vMin (small number) maps to the TOP of the image, vMax to the BOTTOM.
    //
    // For grass_side the green strip is at the top of the image. To render it at the top
    // of the block face, assign vMin to top vertices (by+1) and vMax to bottom vertices (by+0).
    // A naive uniform UV assignment maps V uniformly across both y values, which rotates the
    // texture 90° on side faces because U ends up tracking the vertical Y axis instead of
    // the horizontal block-face axis.
    private static final float[][] FACE_UV_FRACS = {
        {0, 0, 0, 1, 1, 1, 1, 0}, // UP:    U→+X, V→+Z
        {0, 0, 0, 1, 1, 1, 1, 0}, // DOWN:  symmetric, orientation irrelevant
        {1, 1, 0, 1, 0, 0, 1, 0}, // NORTH: U→-X, bottom=vMax, top=vMin
        {0, 1, 1, 1, 1, 0, 0, 0}, // SOUTH: U→+X, bottom=vMax, top=vMin
        {0, 1, 1, 1, 1, 0, 0, 0}, // EAST:  bottom=vMax, top=vMin
        {0, 1, 1, 1, 1, 0, 0, 0}, // WEST:  bottom=vMax, top=vMin
    };

    // Tints for blocks whose textures are stored as greyscale in the pack.
    // Faithful (like vanilla) stores grass_top and leaves as greyscale — the game
    // supplies the biome color. These are hardcoded plains-biome values for now.
    private static final float[] WHITE = {1.00f, 1.00f, 1.00f};
    private static final float[] GRASS_TINT = {0.57f, 0.74f, 0.35f};
    private static final float[] FOLIAGE_TINT = {0.38f, 0.60f, 0.20f};

    private final BlockRegistry registry;
    private final TextureAtlas atlas; // null-safe — tests pass null

    public ChunkMesher(BlockRegistry registry, TextureAtlas atlas) {
        this.registry = registry;
        this.atlas = atlas;
    }

    // Transforms a chunk's block data into renderable geometry.
    // Safe to call from any worker thread — no GL calls, no shared mutable state.
    public ChunkMeshData mesh(Chunk chunk) {
        List<Float> verts = new ArrayList<>();
        List<Integer> idxs = new ArrayList<>();
        int vertCount = 0;

        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                    byte blockId = chunk.getBlock(x, y, z);
                    if (blockId == Block.AIR) continue;

                    BlockDef def = registry.get(blockId);

                    for (Direction dir : Direction.values()) {
                        if (isCulled(chunk, x, y, z, dir)) continue;

                        float[] uvs = getUVs(def, dir);
                        float[] tint = getTint(blockId, dir);
                        vertCount = emitQuad(verts, idxs, x, y, z, dir, uvs, tint, vertCount);
                    }
                }
            }
        }

        return new ChunkMeshData(toFloatArray(verts), toIntArray(idxs), vertCount);
    }

    // A face is culled if its in-chunk neighbor is solid.
    // Out-of-chunk neighbors are never culled — the adjacent chunk's mesher handles
    // its own boundary faces, so we must emit ours to prevent holes at seams.
    private boolean isCulled(Chunk chunk, int x, int y, int z, Direction dir) {
        int[] off = NEIGHBOR_OFFSETS[dir.ordinal()];
        int nx = x + off[0], ny = y + off[1], nz = z + off[2];

        if (nx < 0 || nx >= Chunk.SIZE_XZ || ny < 0 || ny >= Chunk.SIZE_Y || nz < 0 || nz >= Chunk.SIZE_XZ) {
            return false;
        }

        return registry.get(chunk.getBlock(nx, ny, nz)).solid();
    }

    // Writes 4 vertices (40 floats) and 6 indices for one quad into the output lists.
    // Vertex format: x, y, z, u, v, ao, faceId, r, g, b  (10 floats per vertex).
    // Returns the next free vertex base index.
    private int emitQuad(
            List<Float> vertices,
            List<Integer> indices,
            int bx,
            int by,
            int bz,
            Direction dir,
            float[] uvs,
            float[] tint,
            int base) {
        float[][] corners = FACE_VERTICES[dir.ordinal()];
        float faceId = faceIdFor(dir);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        float[] fracs = FACE_UV_FRACS[dir.ordinal()];

        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            vertices.add(bx + c[0]);
            vertices.add(by + c[1]);
            vertices.add(bz + c[2]);
            vertices.add(fracs[i * 2] == 0 ? uMin : uMax);
            vertices.add(fracs[i * 2 + 1] == 0 ? vMin : vMax);
            vertices.add(1.0f); // ao — placeholder; always fully lit until Phase 20
            vertices.add(faceId);
            vertices.add(tint[0]);
            vertices.add(tint[1]);
            vertices.add(tint[2]);
        }

        // Two triangles sharing the diagonal: 0-1-2 and 2-3-0
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base + 2);
        indices.add(base + 3);
        indices.add(base);

        return base + 4;
    }

    private static float faceIdFor(Direction dir) {
        return switch (dir) {
            case UP -> FACE_ID_UP;
            case DOWN -> FACE_ID_DOWN;
            default -> FACE_ID_SIDE;
        };
    }

    // Grass top and all leaf blocks store greyscale textures in the faithful pack —
    // they need a biome color multiplied in. Everything else is white (no tint).
    private static float[] getTint(byte blockId, Direction dir) {
        if (blockId == Block.OAK_LEAF) return FOLIAGE_TINT;
        if (blockId == Block.GRASS && dir == Direction.UP) return GRASS_TINT;
        return WHITE;
    }

    private float[] getUVs(BlockDef def, Direction dir) {
        if (atlas == null) return DEFAULT_UV;
        float[] uvs = atlas.getFaceUVs(def, dir);
        return uvs != null ? uvs : DEFAULT_UV;
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
