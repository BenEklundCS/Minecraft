package com.beneklund.minecraft.platform.images;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryStack;

// Loads PNGs from the classpath via STB. Flips vertically on load so that V=0 is the
// bottom of the image and V=1 is the top — standard OpenGL UV convention throughout.
public class StbImageLoader implements IImageLoader {
    // Shared close handler — all ImageData instances point to this rather than allocating a lambda each time.
    private static final Consumer<ImageData> ON_CLOSE = data -> stbi_image_free(data.pixels());

    @Override
    public ImageData load(String classpathPng) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);

            // Flip so OpenGL's V=0 (bottom of texture) matches the bottom of the image file.
            // Without this, V=0 would map to the top row of the PNG, inverting all textures.
            stbi_set_flip_vertically_on_load(true);
            InputStream stream = getClass().getResourceAsStream(classpathPng);
            if (stream == null) {
                throw new IOException("Texture not found: %s".formatted(classpathPng));
            }
            byte[] bytes = stream.readAllBytes();

            // memAlloc allocates off-heap so STB can read it directly.
            // flip() resets the cursor to 0 after put() so STB reads from the start.
            // memFree releases the staging buffer once STB has finished decoding.
            ByteBuffer fileBytes = memAlloc(bytes.length);
            fileBytes.put(bytes).flip();

            ByteBuffer pixels = stbi_load_from_memory(fileBytes, w, h, c, 4);
            memFree(fileBytes);
            if (pixels == null) {
                throw new IOException("STB failed to decode: %s".formatted(classpathPng));
            }
            int width = w.get();
            int height = h.get();
            int channels = c.get();

            return new ImageData(pixels, width, height, channels, ON_CLOSE);
        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", classpathPng);
            throw new RuntimeException(e);
        }
    }
}
