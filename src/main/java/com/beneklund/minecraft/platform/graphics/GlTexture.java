package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.platform.images.ImageData;
import com.beneklund.minecraft.platform.images.ImageLoader;
import com.beneklund.minecraft.platform.images.StbImageLoader;

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
    private ImageData data;
    private ImageLoader loader;

    public GlTexture() {
        this.loader = new StbImageLoader();
    }

    public void load(String classpathPng) {
        this.data = loader.load(classpathPng);
    }

    public void upload() {
        this.id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.id);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                this.data.width(),
                this.data.height(),
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                this.data.pixels());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        data.close();
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, this.id);
    }

    public void delete() {
        if (this.data.pixels() != null) {
            data.close();
        }
        glDeleteTextures(this.id);
    }
}
