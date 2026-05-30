package com.beneklund.minecraft.platform.graphics;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;

/*
 * Wraps an OpenGL 2D texture. Load and upload are intentionally separate steps -
 * load() is pure CPU work (read file, decode PNG), upload() is where GL gets involved.
 * load() is safe to call off the main thread; upload() is not.
 *
 * STB gives back a ByteBuffer of raw RGBA pixel data. Once that's uploaded to the GPU
 * via glTexImage2D, the CPU copy is dead weight - stbi_image_free() releases it.
 * After upload(), pixels is null and the texture lives entirely on the GPU.
 *
 * GL_NEAREST filter is critical for pixel art. Without it OpenGL defaults to GL_LINEAR,
 * which blurs between adjacent pixels when scaling, so blocks would look smeared.
 *
 * Lifecycle: new -> load() -> upload() -> bind() each frame -> delete() on shutdown.
 */
public class GlTexture {
    private int id;
    private ByteBuffer pixels;
    private int width;
    private int height;

    public GlTexture() {

    }

    public void load(String classpathPng) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            InputStream stream = GlTexture.class.getResourceAsStream(classpathPng);
            if (stream == null) { throw new IOException("Texture not found: " + classpathPng); }
            byte[] bytes = stream.readAllBytes();

            // memAlloc allocates off-heap so STB can read it directly.
            // flip() resets the cursor to 0 after put() so STB reads from the start.
            // memFree releases the staging buffer once STB has finished decoding.
            ByteBuffer fileBytes = memAlloc(bytes.length);
            fileBytes.put(bytes).flip();

            this.pixels = stbi_load_from_memory(fileBytes, w, h, channels, 4);
            memFree(fileBytes);
            if (this.pixels == null) { throw new IOException("STB failed to decode: " + classpathPng); }
            this.width = w.get();
            this.height = h.get();
        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", classpathPng);
            throw new RuntimeException(e);
        }
    }

    public void upload() {
        this.id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, this.width, this.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, this.pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        freePixels();
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, this.id);
    }

    public void delete() {
        if (this.pixels != null) {
            freePixels();
        }
        glDeleteTextures(this.id);
    }

    private void freePixels() {
        stbi_image_free(this.pixels);
        this.pixels = null;
    }
}
