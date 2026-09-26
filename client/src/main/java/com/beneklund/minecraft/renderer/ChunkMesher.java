package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.platform.graphics.Geometry;
import com.beneklund.minecraft.platform.graphics.VertexFormat;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.Direction;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.gen.Biome;
import java.util.List;

/**
 * Turns a chunk's blocks into {@link ChunkMeshData}: one {@link Geometry} for the opaque pass and
 * one for the transparent pass, one quad per visible block face.
 *
 * <p>Makes no GL calls and shares no mutable state, so meshing workers call it concurrently; the
 * main thread uploads the result.
 *
 * <p><b>Face culling.</b> A face is emitted only when the neighbour across it is non-opaque and a
 * different block, so the solid interior of the terrain and the inside of a lake produce nothing.
 * Neighbours across chunk edges come from {@link ChunkWithNeighbors}.
 *
 * <p><b>Vertex ambient occlusion.</b> Each corner of a face looks at the three blocks touching it
 * in the layer in front of the face: two sides and the diagonal. Two opaque sides give the darkest
 * level regardless of the diagonal; otherwise the level is 3 minus the opaque count. The level is
 * ramped through {@code AO_RAMP}, and the quad's triangle split follows the AO so the gradient
 * stays smooth. Sky and block light are averaged per corner over the same four samples plus the
 * block directly in front, with opaque samples counting as dark.
 *
 * <p>Vertex layout is {@link VertexFormat#CHUNK}, and the float order in {@code fillBufferVerts}
 * must match it: position (3), atlas UV (2), AO (1), face id (1), biome tint (3), sky and block
 * light (2, each 0 to 1).
 *
 * @see <a href="https://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/">0fps:
 *     Ambient occlusion for Minecraft-like worlds</a>
 * @see <a href="https://learnopengl.com/Advanced-OpenGL/Face-culling">LearnOpenGL: Face culling</a>
 */
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

    /** Vertex brightness by AO level: 0 is the deepest inside corner, 3 is fully exposed. */
    private static final float[] AO_RAMP = {0.45f, 0.68f, 0.82f, 1.0f};

    /**
     * The quad's six indices under each of its two diagonal splits. The GPU interpolates per
     * triangle, so a lone dark corner off the split diagonal shades only one triangle and draws a
     * hard line across the face. Splitting along the darker pair puts that corner in both
     * triangles. Both orderings walk the same ring, so winding stays CCW from outside.
     */
    private static final int[] QUAD_DIAGONAL_02 = {0, 1, 2, 2, 3, 0};

    private static final int[] QUAD_DIAGONAL_13 = {1, 2, 3, 3, 0, 1};

    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = VertexFormat.CHUNK.floatsPerVertex();
    private static final int INDICES_PER_QUAD = 6;
    // Starting capacity covers a typical surface chunk without needing to grow.
    private static final int INITIAL_FACE_CAPACITY = 8192;

    private static final float[] DEFAULT_UV = {0f, 0f, 1f, 1f};

    /**
     * Which tile edge each face corner takes, as {@code [u0,v0, u1,v1, u2,v2, u3,v3]} where 0
     * selects {@code uMin}/{@code vMin} and 1 selects {@code uMax}/{@code vMax}, in {@code
     * FACE_VERTICES} corner order.
     *
     * <p>STB flips images on load, so V runs bottom to top as OpenGL expects, and side faces map
     * bottom corners to {@code vMin}. Per-face tables keep U on each side face's horizontal axis;
     * one shared table would rotate side textures 90 degrees.
     */
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

    /**
     * Meshes the centre chunk of {@code cn}. Faces go to the transparent geometry when the block's
     * definition is {@code blended()}, otherwise to the opaque geometry. Sections that are entirely
     * air are skipped whole.
     */
    public ChunkMeshData mesh(ChunkPos pos, ChunkWithNeighbors cn) {
        ChunkMeshingBuffer opaque = getBuffer();
        ChunkMeshingBuffer transparent = getBuffer();

        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            if (cn.center().sectionEmptyAt(y)) {
                y += ChunkSection.SIZE - 1;
                continue;
            }
            for (int z = 0; z < Chunk.SIZE_XZ; z++) {
                for (int x = 0; x < Chunk.SIZE_XZ; x++) {
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

    /**
     * Picks the diagonal through the brighter pair of corners, so the darker pair shares both
     * triangles. Compares AO levels rather than ramped floats: the ramp is monotonic, so the answer
     * is the same, and integers make it exact.
     */
    protected static int[] quadOrder(int[] ao) {
        return (ao[0] + ao[2] > ao[1] + ao[3]) ? QUAD_DIAGONAL_13 : QUAD_DIAGONAL_02;
    }

    /**
     * Whether the face of the block at {@code (x, y, z)} facing {@code dir} is hidden: the neighbour
     * is opaque, or is the same block, which drops internal surfaces inside water or glass. Faces
     * at the top and bottom of the world are always kept.
     *
     * <p>When the neighbouring chunk isn't loaded, opaque blocks keep the face, or the world is
     * see-through at the render edge. Transparent blocks cull it: a lake spans many chunks, so the
     * neighbour is nearly always more water, and an emitted face would stand as a water wall at the
     * seam until a remesh. Culling is correct the moment a matching neighbour arrives; a mismatch
     * costs one missing face on the outermost loaded chunk.
     */
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

    /**
     * Sky light at one face corner, in light levels: the mean over the block in front of the face and the
     * corner's three AO neighbours, with opaque samples counting as 0.
     */
    private float vertexSkyLightLevel(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, int corner) {
        List<int[]> offsets = getOffsets(dir, corner).asList();
        float accumulator = 0.0f;
        for (int[] off : offsets) {
            if (opaqueAt(cn, x, y, z, off)) accumulator += 0;
            else accumulator += cn.skyLightAt(x, y, z, off);
        }
        return accumulator / offsets.size();
    }

    /** Block light at one face corner, averaged like {@link #vertexSkyLightLevel}. */
    private float vertexBlockLightLevel(ChunkWithNeighbors cn, int x, int y, int z, Direction dir, int corner) {
        List<int[]> offsets = getOffsets(dir, corner).asList();
        float accumulator = 0.0f;
        for (int[] off : offsets) {
            if (opaqueAt(cn, x, y, z, off)) accumulator += 0;
            else accumulator += cn.blockLightAt(x, y, z, off);
        }
        return accumulator / offsets.size();
    }

    /**
     * Neighbour offsets for one face corner, relative to the block: {@code off} is the block in
     * front of the face; the other three lie in that same layer, touching the corner.
     */
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

    /**
     * The 0fps corner AO level from which of the three neighbours are opaque. Two opaque sides
     * block the diagonal from view, so they give 0 whatever the diagonal holds.
     */
    protected static int aoLevelFormula(boolean side1, boolean side2, boolean diag) {
        if (side1 && side2) return 0;
        return 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (diag ? 1 : 0));
    }

    /**
     * Frames a face corner: {@code n} is the face normal's axis, {@code a} and {@code b} the two
     * axes in the face's plane, and {@code sa}, {@code sb} the direction (-1 or +1) from the block
     * centre toward the corner along each.
     */
    private Sample getSample(int[] off, float[] c) {
        int n = (off[0] != 0) ? 0 : (off[1] != 0) ? 1 : 2;
        int a = (n == 0) ? 1 : 0;
        int b = (n == 2) ? 1 : 2;

        int sa = 2 * (int) c[a] - 1;
        int sb = 2 * (int) c[b] - 1;
        return new Sample(n, a, b, sa, sb, off);
    }

    /**
     * A face corner's frame. {@code n}, {@code a} and {@code b} are axis indices (which slot of an
     * xyz array); {@code sa}, {@code sb} and {@code off} are offsets (how far to move along an
     * axis).
     */
    private record Sample(int n, int a, int b, int sa, int sb, int[] off) {}

    /**
     * The neighbour in the layer in front of the face, stepped {@code stepA} along axis {@code a}
     * and {@code stepB} along {@code b}. {@code (sa, 0)} and {@code (0, sb)} give the two sides,
     * {@code (sa, sb)} the diagonal.
     */
    private int[] getOffset(Sample f, int stepA, int stepB) {
        int[] arr = new int[3];
        arr[f.n()] = f.off()[f.n()];
        arr[f.a()] = stepA;
        arr[f.b()] = stepB;
        return arr;
    }

    /**
     * The face's {@link Direction} ordinal. {@code chunk.frag} derives both the brightness band and
     * the surface normal from it, so all six faces keep distinct ids.
     *
     * <p>The normal was once recovered in the shader from screen-space derivatives, which depend on
     * the view: at grazing angles the derivatives run nearly parallel, their cross product
     * collapses, and the normal lands on the wrong axis. The normal feeds the slope-scaled shadow
     * bias, so whole faces flipped between lit and shadowed as the mouse moved. The mesher knows
     * the normal exactly.
     *
     * <p>Ordinal because {@code FACE_VERTICES} is indexed by it, keeping one order in one place.
     */
    private static float faceIdFor(Direction dir) {
        return dir.ordinal();
    }

    /**
     * Biome colour multiplied into the texture. Grass tops and leaves are greyscale in the pack and
     * take the biome tint; everything else is white.
     */
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
