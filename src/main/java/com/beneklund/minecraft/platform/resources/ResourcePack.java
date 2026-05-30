package com.beneklund.minecraft.platform.resources;

import com.beneklund.minecraft.platform.images.ImageData;
import java.util.Map;

public interface ResourcePack {
    String getName();

    String getAuthor();

    String getLicense();

    int getTileSize();

    Map<String, ImageData> loadTiles();
}
