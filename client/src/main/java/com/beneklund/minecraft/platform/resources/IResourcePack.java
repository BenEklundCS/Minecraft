package com.beneklund.minecraft.platform.resources;

import com.beneklund.minecraft.platform.images.ImageData;
import java.util.Map;

/** A named set of square block textures, all the same size, stitched into the texture atlas. */
public interface IResourcePack {
    String getName();

    String getAuthor();

    String getLicense();

    /** Edge length in pixels of every tile in the pack. */
    int getTileSize();

    /**
     * Decodes every tile, keyed by tile name, in a stable order so the atlas layout is the same on
     * every run. The caller closes each {@link ImageData} after uploading it.
     */
    Map<String, ImageData> loadTiles();
}
