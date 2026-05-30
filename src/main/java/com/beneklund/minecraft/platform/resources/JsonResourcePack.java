package com.beneklund.minecraft.platform.resources;

import com.beneklund.minecraft.platform.images.ImageData;
import com.beneklund.minecraft.platform.images.ImageLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class JsonResourcePack implements ResourcePack {
    private final ImageLoader loader;
    private final String name;
    private final int tileSize;
    private final String author;
    private final String license;
    private final Map<String, String> tilePaths = new LinkedHashMap<>();

    public JsonResourcePack(String classpathJson, ImageLoader loader) throws IOException {
        this.loader = loader;
        InputStream stream = Objects.requireNonNull(
                getClass().getResourceAsStream(classpathJson), "pack not found: %s".formatted(classpathJson));
        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        this.name = root.get("name").getAsString();
        this.author = root.get("author").getAsString();
        this.license = root.get("license").getAsString();
        this.tileSize = root.get("tileSize").getAsInt();

        String baseDir = classpathJson.substring(0, classpathJson.lastIndexOf('/') + 1);
        for (var entry : root.getAsJsonObject("tiles").entrySet()) {
            tilePaths.put(entry.getKey(), baseDir + entry.getValue().getAsString());
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getAuthor() {
        return this.author;
    }

    @Override
    public String getLicense() {
        return this.license;
    }

    @Override
    public int getTileSize() {
        return this.tileSize;
    }

    @Override
    public Map<String, ImageData> loadTiles() {
        Map<String, ImageData> data = new LinkedHashMap<>();
        for (var entry : this.tilePaths.entrySet()) {
            data.put(entry.getKey(), this.loader.load(entry.getValue()));
        }
        return data;
    }
}
