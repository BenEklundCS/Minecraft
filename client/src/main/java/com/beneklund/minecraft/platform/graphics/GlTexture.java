package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.platform.images.IImageLoader;
import com.beneklund.minecraft.platform.images.ImageData;
import com.beneklund.minecraft.platform.images.StbImageLoader;
import java.nio.ByteBuffer;

/**
 * An RGBA8 2D texture, sampled with nearest-neighbour filtering.
 *
 * <p>Loading and uploading are separate steps. {@link #load} decodes a PNG on the CPU and is safe
 * off the main thread; {@link #upload()} copies the pixels to the GPU and frees the decoded buffer,
 * after which the texture exists only in video memory.
 *
 * <p>Both filters are {@code GL_NEAREST} because block textures are pixel art: linear
 * filtering blends neighbouring texels and smears the edges. There are no mipmaps, and {@code
 * GL_NEAREST} as the min filter is what keeps the texture complete without them.
 *
 * <p>Lifecycle: {@link #load}, {@link #upload()}, {@link #bind()} per draw, {@link #delete()}.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Sampler_Object#Filtering">OpenGL Wiki: Texture
 *     filtering</a>
 * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glTexImage2D.xhtml">
 *     glTexImage2D</a>
 */
public final class GlTexture {
    private static final IImageLoader LOADER = new StbImageLoader();

    private int id;
    private ImageData data;

    /** Decodes a PNG from the classpath into CPU memory. Touches no GL state. */
    public void load(String classpathPng) {
        data = LOADER.load(classpathPng);
    }

    /** Uploads the image from {@link #load} and frees its CPU copy. Main thread only. */
    public void upload() {
        upload(data.pixels(), data.width(), data.height());
        data.close();
        data = null;
    }

    /**
     * Uploads caller-built RGBA8 pixels, as {@code TextureAtlas} does after stitching. The caller
     * keeps ownership of {@code pixels} and frees it after this returns.
     */
    public void upload(ByteBuffer pixels, int width, int height) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    /** Binds to {@code GL_TEXTURE_2D} on the active texture unit. */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void delete() {
        // data is normally already freed in upload(); only non-null if load() ran without
        // upload(). The atlas path uses the upload(ByteBuffer) overload and never sets data.
        if (data != null) {
            data.close();
            data = null;
        }
        glDeleteTextures(id);
    }
}
