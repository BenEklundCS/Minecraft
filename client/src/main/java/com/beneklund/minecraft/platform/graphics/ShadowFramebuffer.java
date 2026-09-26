package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

/**
 * The depth-only render target for cascaded shadow maps: one square {@code GL_DEPTH_COMPONENT24}
 * array texture, one layer per cascade.
 *
 * <p>Each cascade renders the scene from the sun into its own layer via {@link #bindLayer}. The
 * terrain shader then samples the whole stack through one {@code sampler2DArray}, picking the
 * layer per fragment by view distance.
 *
 * <p>Its size is a quality setting independent of the window, so there is no resize. It has no
 * colour attachment; draw and read buffers are {@code GL_NONE}, which is what makes a depth-only
 * framebuffer complete.
 *
 * @see <a href="https://learnopengl.com/Advanced-Lighting/Shadows/Shadow-Mapping">LearnOpenGL:
 *     Shadow Mapping</a>
 * @see <a href="https://learnopengl.com/Guest-Articles/2021/CSM">LearnOpenGL: Cascaded Shadow
 *     Mapping</a>
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/dxtecharts/cascaded-shadow-maps">
 *     Microsoft: Cascaded Shadow Maps</a>
 * @see <a href="https://wikis.khronos.org/opengl/Array_Texture">OpenGL Wiki: Array Texture</a>
 */
public class ShadowFramebuffer {
    private final int fbo;
    private final int depthTexture;
    private final int size;
    private final int layers;

    public ShadowFramebuffer(int size, int layers) {
        this.size = size;
        this.layers = layers;

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTexture);
        // An array texture, one layer per cascade. A single object rather than N framebuffers
        // because the shader samples it with one sampler2DArray: GLSL 3.30 requires an array OF
        // samplers to be indexed by a dynamically uniform expression, and the cascade a fragment
        // falls in is chosen per fragment, which is exactly what that forbids.
        //
        // GL_DEPTH_COMPONENT with a null pixel pointer: allocate storage, upload nothing. The
        // format arguments still have to describe a depth image even though no data is passed.
        glTexImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,
                GL_DEPTH_COMPONENT24,
                size,
                size,
                layers,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                (ByteBuffer) null);

        // GL_NEAREST, not GL_LINEAR. Interpolating between two depths produces a value that is
        // not the depth of anything — the midpoint between a near surface and a far one is empty
        // air, and comparing against it reports shadow where there is none. Softening happens by
        // averaging the *comparisons* (PCF in the shader), never the depths.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Anything outside the sun's box must read as "nothing was in the way". CLAMP_TO_EDGE
        // would smear the border texels outward and hang a shadow off the edge of the map across
        // the rest of the world; the border colour is depth 1.0, the far plane, which reads as lit.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BORDER_COLOR, new float[] {1.0f, 1.0f, 1.0f, 1.0f});

        // Layer 0 just so the framebuffer is complete at construction; bindLayer picks the real
        // one per pass.
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0, 0);

        // Both of these are state on *this* framebuffer object, not global state, so they are set
        // once here and never restored — binding another framebuffer does not inherit them.
        // Without them GL expects a colour attachment and reports the framebuffer incomplete.
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        validate();
    }

    /**
     * Binds this framebuffer with its depth attachment on {@code layer} and sets the viewport to
     * the map size.
     *
     * <p>The caller clears {@code GL_DEPTH_BUFFER_BIT} after this call. A clear applies to the layer
     * attached at that moment, so clearing first would wipe the previous cascade.
     */
    public void bindLayer(int layer) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0, layer);
        glViewport(0, 0, size, size);
    }

    public int layers() {
        return layers;
    }

    public void delete() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTexture);
    }

    /** The array texture name, for binding to {@code GL_TEXTURE_2D_ARRAY} on a texture unit. */
    public int depthTexture() {
        return depthTexture;
    }

    public int size() {
        return size;
    }

    private void validate() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            delete();
            throw new IllegalStateException(
                    "shadow framebuffer incomplete: 0x%s".formatted(Integer.toHexString(status)));
        }
    }
}
