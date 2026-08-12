package com.beneklund.minecraft.renderer;

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

public class TextureAtlas {
    private GlTexture textureAtlas;
    private final Map<String, float[]> uvCache = new HashMap<>();

    public TextureAtlas(IResourcePack pack) {
        Map<String, ImageData> tiles = pack.loadTiles();
        int count = tiles.size(); // number of tiles in the pack
        int tileSize = pack.getTileSize(); // pixel size of each tile

        // Arrange tiles in a square-ish grid.
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int atlasW = cols * tileSize;
        int atlasH = rows * tileSize;

        // Allocate one off-heap zeroed RGBA buffer for the whole atlas.
        ByteBuffer atlasBuf = memAlloc(atlasW * atlasH * 4); // 4 bytes per pixel

        // Iterate tiles with an index so you know each tile's grid slot.
        // col = i % cols, row = i / cols.
        // For each tile: copyTile() the pixels in, compute+cache its UVs, then
        // close() the ImageData to free the STB off-heap memory - you're done with it.
        int i = 0;
        for (var entry : tiles.entrySet()) {
            int col = i % cols;
            int row = i / cols;

            copyTile(entry.getValue(), atlasBuf, col, row, tileSize, atlasW);
            uvCache.put(entry.getKey(), computeUVs(col, row, tileSize, atlasW, atlasH));
            entry.getValue().close();
            i++;
        }

        // Upload the stitched atlas to GL, then memFree(atlasBuf).
        // Same pattern as GlTexture.upload(): glGenTextures -> glBindTexture(GL_TEXTURE_2D)
        // -> glTexImage2D. Use GL_NEAREST for both filters (pixel art, no blurring).
        // After glTexImage2D the GPU owns the data - free the CPU buffer with memFree.
        upload(atlasBuf, atlasW, atlasH);
        RENDER.info(
                "atlas {}x{} from {} tile(s) at {}px ({} grid)", atlasW, atlasH, count, tileSize, cols + "x" + rows);
        if (RENDER.isDebugEnabled()) {
            RENDER.debug("atlas tiles: {}", uvCache.keySet());
        }
    }

    // Copy one tile's pixels into the right slot in the atlas buffer.
    // STB pixels are row-major RGBA, top-to-bottom. For each row y (0..tileSize-1):
    //   src byte offset: y * tileSize * 4
    //   dst byte offset: ((row * tileSize + y) * atlasW + col * tileSize) * 4
    // Copy tileSize*4 bytes per row. Easiest approach: loop x (0..tileSize*4-1) and
    // do atlas.put(dstOffset + x, tile.pixels().get(srcOffset + x)).
    private void copyTile(ImageData tile, ByteBuffer atlas, int col, int row, int tileSize, int atlasW) {
        for (int y = 0; y < tileSize; y++) {
            int srcByteOffset = y * tileSize * 4;
            int dstByteOffset = ((row * tileSize + y) * atlasW + col * tileSize) * 4;
            for (int x = 0; x < tileSize * 4; x++) {
                atlas.put(dstByteOffset + x, tile.pixels().get(srcByteOffset + x));
            }
        }
    }

    // Tile's position in the atlas normalized to [0,1] UV space.
    // uMin = (col * tileSize) / (float) atlasW
    // uMax = ((col + 1) * tileSize) / (float) atlasW
    // vMin/vMax same pattern with row and atlasH.
    // Return float[]{uMin, vMin, uMax, vMax} - renderer unpacks the 4 corners per-vertex.
    // V orientation: STB flips images on load, so vMin maps to the BOTTOM of the image and
    // vMax to the TOP — standard OpenGL convention. ChunkMesher's FACE_UV_FRACS assigns
    // vMin to bottom vertices and vMax to top vertices on side faces.
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

    // Create a GlTexture, call texture.upload(atlas, atlasW, atlasH), store the id,
    // then MemoryUtil.memFree(atlas) - GPU has the data, CPU buffer is dead weight.
    private void upload(ByteBuffer atlas, int atlasW, int atlasH) {
        textureAtlas = new GlTexture();
        textureAtlas.upload(atlas, atlasW, atlasH);
        memFree(atlas);
    }

    // A missing tile name is a data bug in BlockDef — fail fast so typos surface immediately
    // rather than silently rendering wrong textures.
    public float[] getUVs(String tileName) {
        float[] uvs = uvCache.get(tileName);
        if (uvs == null) throw new IllegalArgumentException("Unknown tile: " + tileName);
        return uvs;
    }

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
