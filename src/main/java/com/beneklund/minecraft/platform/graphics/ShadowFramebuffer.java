package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

// A depth-only framebuffer, for rendering the scene from the sun's point of view.
public class ShadowFramebuffer {
    private final int fbo;
    private final int depthTexture;
    private final int size;

    public ShadowFramebuffer(int size) {
        this.size = size;

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        // GL_DEPTH_COMPONENT with a null pixel pointer: allocate storage, upload nothing. The
        // format arguments still have to describe a depth image even though no data is passed.
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);

        // GL_NEAREST, not GL_LINEAR. Interpolating between two depths produces a value that is
        // not the depth of anything — the midpoint between a near surface and a far one is empty
        // air, and comparing against it reports shadow where there is none. Softening happens by
        // averaging the *comparisons* (PCF in the shader), never the depths.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Anything outside the sun's box must read as "nothing was in the way". CLAMP_TO_EDGE
        // would smear the border texels outward and hang a shadow off the edge of the map across
        // the rest of the world; the border colour is depth 1.0, the far plane, which reads as lit.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[] {1.0f, 1.0f, 1.0f, 1.0f});

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);

        // Both of these are state on *this* framebuffer object, not global state, so they are set
        // once here and never restored — binding another framebuffer does not inherit them.
        // Without them GL expects a colour attachment and reports the framebuffer incomplete.
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        validate();
    }

    // Callers must clear GL_DEPTH_BUFFER_BIT after this — there is no colour buffer to clear.
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, size, size);
    }

    public void delete() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTexture);
    }

    // Bind this to a texture unit and hand the unit number to the shader's sampler.
    public int depthTexture() {
        return depthTexture;
    }

    public int size() {
        return size;
    }

    // No resize(). The shadow map's resolution is a quality setting, not a function of the
    // window — GlFramebuffer resizes because it is screen-sized and this is not.
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
