package com.beneklund.minecraft.platform.resources;

import static com.beneklund.minecraft.util.Log.IO;

import com.beneklund.minecraft.platform.images.IImageLoader;
import com.beneklund.minecraft.platform.images.ImageData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class JsonResourcePack implements IResourcePack {
    private final IImageLoader loader;
    private final String name;
    private final int tileSize;
    private final String author;
    private final String license;
    private final Map<String, String> tilePaths = new LinkedHashMap<>();

    public JsonResourcePack(String classpathJson, IImageLoader loader) throws IOException {
        this.loader = loader;
        InputStream stream = Objects.requireNonNull(
                getClass().getResourceAsStream(classpathJson), "pack not found: %s".formatted(classpathJson));
        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        name = root.get("name").getAsString();
        author = root.get("author").getAsString();
        license = root.get("license").getAsString();
        tileSize = root.get("tileSize").getAsInt();

        // Tile paths in the JSON are relative to the pack file itself, so prefix with the
        // pack's directory so ImageLoader gets a full classpath path.
        String baseDir = classpathJson.substring(0, classpathJson.lastIndexOf('/') + 1);
        for (var entry : root.getAsJsonObject("tiles").entrySet()) {
            tilePaths.put(entry.getKey(), baseDir + entry.getValue().getAsString());
        }
        IO.info(
                "resource pack \"{}\" by {} ({}), {} tile(s) at {}px",
                name,
                author,
                license,
                tilePaths.size(),
                tileSize);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getLicense() {
        return license;
    }

    @Override
    public int getTileSize() {
        return tileSize;
    }

    @Override
    public Map<String, ImageData> loadTiles() {
        // LinkedHashMap preserves insertion order, which keeps atlas stitching deterministic.
        Map<String, ImageData> data = new LinkedHashMap<>();
        for (var entry : tilePaths.entrySet()) {
            IO.trace("decoding tile {} from {}", entry.getKey(), entry.getValue());
            data.put(entry.getKey(), loader.load(entry.getValue()));
        }
        return data;
    }
}
