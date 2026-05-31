package com.beneklund.minecraft.platform.resources;

import com.beneklund.minecraft.platform.images.ImageData;
import java.util.Map;

public interface ResourcePack {
    String getName();

    String getAuthor();

    String getLicense();

    // Width (and height) in pixels of a single tile. All tiles in a pack must be the same size.
    int getTileSize();

    // Returns one ImageData per named tile. Callers must close each ImageData after uploading to GL.
    Map<String, ImageData> loadTiles();
}
