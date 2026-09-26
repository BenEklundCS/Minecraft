package com.beneklund.minecraft.renderer.asset;

import static com.beneklund.minecraft.util.Log.RENDER;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.platform.graphics.GlTexture;
import com.beneklund.minecraft.platform.images.ImageData;
import com.beneklund.minecraft.platform.resources.IResourcePack;
import com.beneklund.minecraft.util.Direction;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Every block texture in the resource pack stitched into one GL texture, so all terrain draws with
 * one texture bind.
 *
 * <p>Tiles are laid out row-major in a near-square grid of {@code ceil(sqrt(n))} columns. Each
 * tile's UV rectangle is cached by name as {@code {uMin, vMin, uMax, vMax}}, inset by half a texel
 * on every edge so nearest-neighbour sampling at a tile's border never reads its neighbour's
 * pixels. Images are flipped on load, so {@code vMin} is the bottom of the tile.
 *
 * <p>The atlas has no mipmaps. The half-texel inset is enough at the base level; mipmapping an
 * atlas would need padding between tiles to keep lower levels from blending neighbours.
 */
public class TextureAtlas {
    private GlTexture textureAtlas;
    private final Map<String, float[]> uvCache = new HashMap<>();

    public TextureAtlas(IResourcePack pack) {
        Map<String, ImageData> tiles = pack.loadTiles();
        int count = tiles.size();
        int tileSize = pack.getTileSize();

        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int atlasW = cols * tileSize;
        int atlasH = rows * tileSize;

        ByteBuffer atlasBuf = memAlloc(atlasW * atlasH * 4);

        int i = 0;
        for (var entry : tiles.entrySet()) {
            int col = i % cols;
            int row = i / cols;

            copyTile(entry.getValue(), atlasBuf, col, row, tileSize, atlasW);
            uvCache.put(entry.getKey(), computeUVs(col, row, tileSize, atlasW, atlasH));
            entry.getValue().close();
            i++;
        }

        upload(atlasBuf, atlasW, atlasH);
        RENDER.info(
                "atlas {}x{} from {} tile(s) at {}px ({} grid)", atlasW, atlasH, count, tileSize, cols + "x" + rows);
        if (RENDER.isDebugEnabled()) {
            RENDER.debug("atlas tiles: {}", uvCache.keySet());
        }
    }

    /** Copies one RGBA8 tile, row by row, into its grid slot in the atlas buffer. */
    private void copyTile(ImageData tile, ByteBuffer atlas, int col, int row, int tileSize, int atlasW) {
        for (int y = 0; y < tileSize; y++) {
            int srcByteOffset = y * tileSize * 4;
            int dstByteOffset = ((row * tileSize + y) * atlasW + col * tileSize) * 4;
            for (int x = 0; x < tileSize * 4; x++) {
                atlas.put(dstByteOffset + x, tile.pixels().get(srcByteOffset + x));
            }
        }
    }

    /**
     * The tile's UV rectangle as {@code {uMin, vMin, uMax, vMax}}. Images are flipped on load, so
     * {@code vMin} is the bottom of the tile and {@code vMax} the top; {@code ChunkMesher} maps
     * side faces' bottom corners to {@code vMin}.
     */
    private float[] computeUVs(int col, int row, int tileSize, int atlasW, int atlasH) {
        // Inset by half a texel on each edge so GL_NEAREST never rounds across a tile boundary
        // and samples a pixel from an adjacent tile in the atlas.
        float halfU = 0.5f / atlasW;
        float halfV = 0.5f / atlasH;
        float uMin = (col * tileSize) / (float) atlasW + halfU;
        float uMax = ((col + 1) * tileSize) / (float) atlasW - halfU;
        float vMin = (row * tileSize) / (float) atlasH + halfV;
        float vMax = ((row + 1) * tileSize) / (float) atlasH - halfV;

        return new float[] {uMin, vMin, uMax, vMax};
    }

    /** Uploads the stitched atlas and frees the CPU buffer, which the GPU copy replaces. */
    private void upload(ByteBuffer atlas, int atlasW, int atlasH) {
        textureAtlas = new GlTexture();
        textureAtlas.upload(atlas, atlasW, atlasH);
        memFree(atlas);
    }

    /**
     * The UV rectangle for a tile, as {@code {uMin, vMin, uMax, vMax}}.
     *
     * @throws IllegalArgumentException for an unknown tile name, which is a typo in a {@code
     *     BlockDef}; it fails loudly so the typo surfaces at once
     */
    public float[] getUVs(String tileName) {
        float[] uvs = uvCache.get(tileName);
        if (uvs == null) throw new IllegalArgumentException("Unknown tile: " + tileName);
        return uvs;
    }

    /** The UV rectangle for the tile {@code def} shows on its {@code dir} face. */
    public float[] getFaceUVs(BlockDef def, Direction dir) {
        return getUVs(def.getTileFace(dir));
    }

    public void bind() {
        textureAtlas.bind();
    }

    public void delete() {
        RENDER.debug("deleting atlas texture");
        textureAtlas.delete();
    }
}
