package com.beneklund.minecraft.platform.images;

import static com.beneklund.minecraft.util.Log.IO;
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

/**
 * Decodes classpath images with stb_image into RGBA8, four bytes per pixel whatever the source
 * format, bottom row first.
 *
 * <p>GL puts texture coordinate V=0 at the first row uploaded, and image files store the top row
 * first. Flipping on load makes V=0 the bottom of the image, so every UV in the codebase uses GL's
 * convention.
 *
 * <p>stb reads from memory, so the file's bytes are copied into a native staging buffer, decoded,
 * and the staging buffer freed. The decoded pixels are freed by {@code stbi_image_free} when the
 * {@link ImageData} closes.
 *
 * @see <a href="https://github.com/nothings/stb/blob/master/stb_image.h">stb_image.h</a>
 */
public class StbImageLoader implements IImageLoader {
    private static final Consumer<ImageData> ON_CLOSE = data -> stbi_image_free(data.pixels());

    @Override
    public ImageData load(String classpathPng) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);

            stbi_set_flip_vertically_on_load(true);
            InputStream stream = getClass().getResourceAsStream(classpathPng);
            if (stream == null) {
                throw new IOException("Texture not found: %s".formatted(classpathPng));
            }
            byte[] bytes = stream.readAllBytes();

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
            IO.error("Failed to load texture: {}", classpathPng);
            throw new RuntimeException(e);
        }
    }
}
