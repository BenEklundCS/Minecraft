package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.platform.graphics.Geometry;
import com.beneklund.minecraft.platform.graphics.VertexFormat;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.Direction;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.ChunkWithNeighbors;
import com.beneklund.minecraft.world.LightMap;
import com.beneklund.minecraft.world.gen.Biome;
import java.util.List;

// Converts a Chunk's block data into a ChunkMeshData — one Geometry for the opaque pass and
// one for the transparent pass. No GL calls, so this is safe on any worker thread; the caller
// uploads the result via ChunkMesh on the main thread.
//
// The layout itself lives in VertexFormat.CHUNK. Don't restate the stride here — it drifts.
// What the slots mean:
//   [0-2]  x, y, z       world position
//   [3-4]  u, v          atlas UV
//   [5]    ao            ambient occlusion, ramped through AO_RAMP
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

    // indexed by AO level, 0 == deepest inside corner, 3 == fully exposed
    private static final float[] AO_RAMP = {0.45f, 0.68f, 0.82f, 1.0f};

    // Which of the quad's 4 vertices each of the 6 indices refers to. The GPU interpolates per
    // triangle, so a lone dark corner that isn't on the split diagonal only shades one of the two
    // triangles and you get a hard line across the face. Picking the diagonal that runs through
    // the darker pair puts that corner in both triangles and the gradient covers the whole quad.
    // Both orderings walk the same ring, so winding stays CCW from outside either way.
    private static final int[] QUAD_DIAGONAL_02 = {0, 1, 2, 2, 3, 0};
    private static final int[] QUAD_DIAGONAL_13 = {1, 2, 3, 3, 0, 1};

    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = VertexFormat.CHUNK.floatsPerVertex();
    private static final int INDICES_PER_QUAD = 6;
    // Starting capacity covers a typical surface chunk without needing to grow.
    private static final int INITIAL_FACE_CAPACITY = 8192;

    // faceId values matched to the brightness bands in chunk.frag:
    //   < 0.5 → 1.0 (bright),  < 2.5 → 0.8 (side),  else → 0.6 (dark)
    private static final float FACE_ID_UP = 0.0f;
    private static final float FACE_ID_SIDE = 1.0f;
    private static final float FACE_ID_DOWN = 2.0f;

    private static final float[] DEFAULT_UV = {0f, 0f, 1f, 1f};

    // Per-face UV fractions: [u0,v0, u1,v1, u2,v2, u3,v3] where 0=uMin/vMin, 1=uMax/vMax.
    // Index order matches FACE_VERTICES — vertex 0 uses fracs[0,1], vertex 1 uses [2,3], etc.
    //
    // STB flips images on load so V=0 = bottom of image, V=1 = top — standard OpenGL convention.
    // Side faces simply map bottom vertices to vMin and top vertices to vMax.
    // Without per-face fracs (uniform UV) the U axis would track the vertical Y instead of
    // the horizontal face axis, rotating side textures 90°.
    private static final float[][] FACE_UV_FRACS = {
        {0, 0, 0, 1, 1, 1, 1, 0}, // UP:    U→+X, V→+Z
        {0, 0, 0, 1, 1, 1, 1, 0}, // DOWN:  symmetric
        {1, 0, 0, 0, 0, 1, 1, 1}, // NORTH: U→-X, bottom=vMin, top=vMax
        {0, 0, 1, 0, 1, 1, 0, 1}, // SOUTH: U→+X, bottom=vMin, top=vMax
        {0, 0, 1, 0, 1, 1, 0, 1}, // EAST:  bottom=vMin, top=vMax
        {0, 0, 1, 0, 1, 1, 0, 1}, // WEST:  bottom=vMin, top=vMax
    };

    // Grass and foliage tints come from the biome. PLAINS is the default until chunks
    // carry per-block biome data and the mesher can look up the correct biome per column.
    private static final Color GRASS_TINT = Biome.PLAINS.grassColor();
    private static final Color FOLIAGE_TINT = Biome.PLAINS.foliageColor();

    private final BlockRegistry registry;
    private final TextureAtlas atlas; // null-safe — tests pass null

    public ChunkMesher(BlockRegistry registry, TextureAtlas atlas) {
        this.registry = registry;
        this.atlas = atlas;
    }

    // Transforms a chunk's block data into renderable geometry.
    // Safe to call from any worker thread — no GL calls, no shared mutable state.
    //
    // Faces are routed into one of two buffers by the block's transparent flag so the
    // renderer can do an opaque pass then a transparent pass (see ChunkRenderable / Renderer).
    public ChunkMeshData mesh(ChunkPos pos, ChunkWithNeighbors cn) {
        ChunkMeshingBuffer opaque = getBuffer();
        ChunkMeshingBuffer transparent = getBuffer();

        for (int x = 0; x < Chunk.SIZE_XZ; x++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                    Block blockId = cn.center().getBlock(x, y, z);
                    if (blockId == Block.AIR) continue;

                    BlockDef def = registry.get(blockId);
                    ChunkMeshingBuffer buf = def.blended() ? transparent : opaque;

                    for (Direction dir : Direction.DIRECTIONS) {
                        if (isCulled(cn, x, y, z, dir, blockId)) continue;

                        buf.ensureQuadCapacity();

                        float[] sky = new float[VERTICES_PER_QUAD];
                        for (int i = 0; i < VERTICES_PER_QUAD; i++)
                            sky[i] = vertexSkyLightLevel(cn, x, y, z, dir, i) / (float) LightMap.MAX_LEVEL;
                        float[] block = new float[VERTICES_PER_QUAD];
                        for (int i = 0; i < VERTICES_PER_QUAD; i++)
                            block[i] = vertexBlockLightLevel(cn, x, y, z, dir, i) / (float) LightMap.MAX_LEVEL;

                        float[] uvs = getUVs(def, dir);
                        Color tint = getTint(blockId, dir);
                        float[][] corners = FACE_VERTICES[dir.ordinal()];
                        float faceId = faceIdFor(dir);
                        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
                        float[] fracs = FACE_UV_FRACS[dir.ordinal()];

                        int[] ao = new int[VERTICES_PER_QUAD];
                        for (int i = 0; i < VERTICES_PER_QUAD; i++) ao[i] = ambientOcclusionLevel(cn, x, y, z, dir, i);

                        for (int i = 0; i < VERTICES_PER_QUAD; i++) {
                            float u = fracs[i * 2] == 0 ? uMin : uMax;
                            float v = fracs[i * 2 + 1] == 0 ? vMin : vMax;

                            fillBufferVerts(
                                    buf, corners[i], x, y, z, u, v, AO_RAMP[ao[i]], faceId, tint, sky[i], block[i]);
                        }
                        fillBufferIdxs(buf, ao);
                    }
                }
            }
        }

        Geometry opaqueGeometry = new Geometry(opaque.copyVertices(), opaque.copyIndices());
        Geometry transparentGeometry = new Geometry(transparent.copyVertices(), transparent.copyIndices());

        return new ChunkMeshData(
                pos, opaqueGeometry, transparentGeometry, opaque.base() + transparent.base(), cn.center());
    }

    private void fillBufferVerts(
            ChunkMeshingBuffer buf,
            float[] c,
            int x,
            int y,
            int z,
            float u,
            float v,
            float ao,
            float f,
            Color t,
            float sky,
            float block) {
        buf.writeVert(x + c[0]);
        buf.writeVert(y + c[1]);
        buf.writeVert(z + c[2]);
        buf.writeVert(u);
        buf.writeVert(v);
        buf.writeVert(ao);
        buf.writeVert(f);
        buf.writeVert(t.red());
        buf.writeVert(t.green());
        buf.writeVert(t.blue());
        buf.writeVert(sky);
        buf.writeVert(block);
    }

    private void fillBufferIdxs(ChunkMeshingBuffer buf, int[] ao) {
        for (int corner : quadOrder(ao)) buf.writeIdx(buf.base() + corner);
        buf.advance();
    }

    // Compare on the levels rather than the ramped floats — same answer since the ramp is
    // monotonic, but integers make it exact.
    protected static int[] quadOrder(int[] ao) {
        return (ao[0] + ao[2] > ao[1] + ao[3]) ? QUAD_DIAGONAL_13 : QUAD_DIAGONAL_02;
    }

    // A face is culled if its neighbor is solid and opaque, or if the neighbor is the same block
    // (so we don't emit internal surfaces inside a body of water or a pane of glass).
    // This applies across chunk boundaries too — resolve() hands back the adjacent chunk and we
    // cull against it just like an in-chunk neighbor.
    //
    // When resolve() comes back empty we don't know what's over there, and the right guess differs
    // by block type. Opaque terrain gets the face — otherwise you see straight through the world at
    // the render edge. Transparent blocks don't: a lake spans many chunks, so the neighbor is
    // nearly always more water, and emitting leaves a water pane standing at the seam that only a
    // later remesh could clear. Culling is right the moment the neighbor turns out to match, so the
    // seam looks correct no matter when the neighbor shows up. When it doesn't match we lose a face
    // on the outermost loaded chunk, which is far cheaper than a wall through the middle of a lake.
    private boolean isCulled(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, Block blockId) {
        int nx = x + dir.dx(), ny = y + dir.dy(), nz = z + dir.dz();

        if (ny < 0 || ny >= Chunk.SIZE_Y) {
            return false;
        }

        if (cn.resolve(nx, nz).isEmpty()) return registry.get(blockId).transparent();
        Block neighbor = cn.blockAt(nx, ny, nz);
        return registry.get(neighbor).opaque() || neighbor == blockId;
    }

    private boolean opaqueAt(ChunkWithNeighbors cn, int x, int y, int z, int[] offset) {
        return registry.get(cn.blockAt(x + offset[0], y + offset[1], z + offset[2]))
                .opaque();
    }

    private int ambientOcclusionLevel(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, int corner) {
        Offsets offsets = getOffsets(dir, corner);

        boolean opaqueAtSide1 = opaqueAt(cn, x, y, z, offsets.side1());
        boolean opaqueAtSide2 = opaqueAt(cn, x, y, z, offsets.side2());
        boolean opaqueAtDiagonal = opaqueAt(cn, x, y, z, offsets.diagonal());

        return aoLevelFormula(opaqueAtSide1, opaqueAtSide2, opaqueAtDiagonal);
    }

    private float vertexSkyLightLevel(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, int corner) {
        List<int[]> offsets = getOffsets(dir, corner).asList();
        float accumulator = 0.0f;
        for (int[] off : offsets) {
            if (opaqueAt(cn, x, y, z, off)) accumulator += 0;
            else accumulator += cn.skyLightAt(x, y, z, off);
        }
        return accumulator / offsets.size(); // average
    }

    private float vertexBlockLightLevel(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, int corner) {
        List<int[]> offsets = getOffsets(dir, corner).asList();
        float accumulator = 0.0f;
        for (int[] off : offsets) {
            if (opaqueAt(cn, x, y, z, off)) accumulator += 0;
            else accumulator += cn.blockLightAt(x, y, z, off);
        }
        return accumulator / offsets.size(); // average
    }

    private record Offsets(int[] off, int[] side1, int[] side2, int[] diagonal) {
        public List<int[]> asList() {
            return List.of(off, side1, side2, diagonal);
        }
    }

    private Offsets getOffsets(Direction dir, int corner) {
        int[] off = new int[] {dir.dx(), dir.dy(), dir.dz()};
        float[] c = FACE_VERTICES[dir.ordinal()][corner];

        Sample frame = getSample(off, c);

        int[] side1 = getOffset(frame, frame.sa(), 0);
        int[] side2 = getOffset(frame, 0, frame.sb());
        int[] diagonal = getOffset(frame, frame.sa(), frame.sb());
        return new Offsets(off, side1, side2, diagonal);
    }

    protected static int aoLevelFormula(boolean side1, boolean side2, boolean diag) {
        if (side1 && side2) return 0;
        return 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (diag ? 1 : 0)); // 3 - (side1 + side2 + corner);
    }

    private Sample getSample(int[] off, float[] c) {
        int n = (off[0] != 0) ? 0 : (off[1] != 0) ? 1 : 2; // normal axis
        int a = (n == 0) ? 1 : 0; // first corner axis
        int b = (n == 2) ? 1 : 2; // second corner axis

        int sa = 2 * (int) c[a] - 1; // 0 → -1, 1 → +1
        int sb = 2 * (int) c[b] - 1;
        return new Sample(n, a, b, sa, sb, off);
    }

    // `n`, `a` and `b` are axis indices. `sa` and `sb` are components of an offset.
    // | **offset**     | `{+1, 1, 0}`  | how far to *move*. Added to a coordinate.                         |
    // | -------------- | ------------- | ----------------------------------------------------------------- |
    // | **axis index** | `0`, `1`, `2` | which *slot* of a 3-element array. |
    private record Sample(int n, int a, int b, int sa, int sb, int[] off) {}

    // offset for side1 - pass 0 to sb
    // offset for side2 - pass 0 to sa
    // offset for diag  - pass sa and sb
    private int[] getOffset(Sample f, int stepA, int stepB) {
        int[] arr = new int[3];
        arr[f.n()] = f.off()[f.n()];
        arr[f.a()] = stepA;
        arr[f.b()] = stepB;
        return arr;
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
    private static Color getTint(Block blockId, Direction dir) {
        if (blockId == Block.OAK_LEAF) return FOLIAGE_TINT;
        if (blockId == Block.GRASS && dir == Direction.UP) return GRASS_TINT;
        return Color.WHITE;
    }

    private float[] getUVs(BlockDef def, Direction dir) {
        if (atlas == null) return DEFAULT_UV; // null atlas = test mode, no GL context
        return atlas.getFaceUVs(def, dir);
    }

    private ChunkMeshingBuffer getBuffer() {
        return new ChunkMeshingBuffer(INITIAL_FACE_CAPACITY, VERTICES_PER_QUAD, FLOATS_PER_VERTEX, INDICES_PER_QUAD);
    }
}
