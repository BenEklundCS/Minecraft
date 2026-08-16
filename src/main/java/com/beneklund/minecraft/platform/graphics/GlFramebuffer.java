package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

public class GlFramebuffer {
    private final int fbo;
    private final int colorTexture;
    private final DepthMode depthMode;

    // The renderbuffer name under RENDERBUFFER, the texture name under TEXTURE, 0 under NONE.
    // One field rather than two because glGen* hands both back as plain ints and the mode already
    // says which namespace this one lives in — a second field would be permanently 0.
    private final int depthAttachment;

    private int width, height;
    private final int internalFormat;

    public GlFramebuffer(int width, int height, int internalFormat, DepthMode depthMode) {
        this.width = width;
        this.height = height;
        this.internalFormat = internalFormat;
        this.depthMode = depthMode;

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);

        depthAttachment = switch (depthMode) {
            case NONE -> 0;
            case RENDERBUFFER -> attachDepthRenderbuffer();
            case TEXTURE -> attachDepthTexture();
        };

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        validate();
    }

    private int attachDepthRenderbuffer() {
        int rbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rbo);
        return rbo;
    }

    private int attachDepthTexture() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        // Same 24-bit depth image the renderbuffer path allocates, just addressable by a sampler.
        // Null pixel pointer: allocate storage, upload nothing — the format arguments still have
        // to describe a depth image even though no data is passed.
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer)
                        null);

        // GL_NEAREST, not GL_LINEAR. A blend of two depths is the depth of nothing: halfway
        // between a near surface and a far one is empty air. Anything that wants a soft result
        // averages what it computed from the samples, never the samples themselves.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Deliberately no GL_TEXTURE_COMPARE_MODE. That turns the sampler into a shadow sampler
        // returning pass/fail against a reference value; readers here want the stored depth back
        // so they can run it through the projection and recover a distance.
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, tex, 0);
        return tex;
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
    }

    public static void bindDefault(int w, int h) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w, h);
    }

    public void delete() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(colorTexture);
        switch (depthMode) {
            case NONE -> {}
            case RENDERBUFFER -> glDeleteRenderbuffers(depthAttachment);
            case TEXTURE -> glDeleteTextures(depthAttachment);
        }
    }

    public int colorTexture() {
        return colorTexture;
    }

    /*
     * Bind this to a texture unit and hand the unit number to the shader's sampler.
     *
     * Throws under the other modes rather than returning 0, because 0 is a legal argument to
     * glBindTexture meaning "no texture" — a caller that asked the wrong framebuffer for its depth
     * would sample black and see a plausible-looking but wrong image instead of a crash.
     */
    public int depthTexture() {
        if (depthMode != DepthMode.TEXTURE) {
            throw new IllegalStateException("framebuffer has no sampleable depth: mode is %s".formatted(depthMode));
        }
        return depthAttachment;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;

        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);

        // Reallocating, not resizing — no GL call grows an existing image. The object names stay
        // the same, so the framebuffer's attachment points still point at the right thing and
        // nothing has to be reattached.
        switch (depthMode) {
            case NONE -> {}
            case RENDERBUFFER -> {
                glBindRenderbuffer(GL_RENDERBUFFER, depthAttachment);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
                glBindRenderbuffer(GL_RENDERBUFFER, 0);
            }
            case TEXTURE -> {
                glBindTexture(GL_TEXTURE_2D, depthAttachment);
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_DEPTH_COMPONENT24,
                        width,
                        height,
                        0,
                        GL_DEPTH_COMPONENT,
                        GL_FLOAT,
                        (ByteBuffer) null);
            }
        }

        glBindTexture(GL_TEXTURE_2D, 0);

        validate();
    }

    private void validate() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            delete();
            throw new IllegalStateException("framebuffer incomplete: 0x%s".formatted(Integer.toHexString(status)));
        }
    }
}
