package com.beneklund.minecraft.platform.graphics;

/**
 * The depth attachment a {@link GlFramebuffer} gets, chosen by whether a later pass samples the
 * depth.
 *
 * <p>A renderbuffer is invisible to shaders, which leaves the driver free to store it in whatever
 * tiled or compressed layout the depth hardware prefers. A depth texture gives that up so it can
 * be bound to a sampler.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Framebuffer_Object#Attaching_images">OpenGL
 *     Wiki: Framebuffer Object, attaching images</a>
 */
public enum DepthMode {
    /**
     * No depth attachment. For fullscreen passes such as blurs and the tonemap, which cover the
     * screen with nothing to depth-sort and would never write depth storage.
     */
    NONE,

    /** Depth for testing and writing only. The default for passes that draw real geometry. */
    RENDERBUFFER,

    /**
     * Depth in a {@code GL_DEPTH_COMPONENT24} texture, for screen-space effects that need each
     * pixel's distance. Read it with {@link GlFramebuffer#depthTexture()}.
     */
    TEXTURE
}
