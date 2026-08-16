package com.beneklund.minecraft.platform.graphics;

/*
 * What kind of depth attachment a GlFramebuffer gets, which decides what can be done with the
 * depth afterwards. The question is not quality, it is "does a later pass read this back".
 *
 * A renderbuffer is opaque to shaders, and that is exactly why the driver is free to keep it in
 * whatever tiled or compressed layout the depth hardware likes. Asking for a texture gives that
 * freedom up in exchange for being able to sample it.
 */
public enum DepthMode {
    // No depth attachment at all. Fullscreen-quad passes — a blur, a tonemap — draw two triangles
    // covering the screen with nothing to sort against, so depth storage would be allocated,
    // reallocated on every window resize, and never written to.
    NONE,

    // Depth that can be tested and written but never read from a shader. The default for anything
    // that draws real geometry and is then only looked at as colour.
    RENDERBUFFER,

    // Depth backed by a texture, so a later pass can bind it to a texture unit and sample it.
    // What screen-space effects need when they have to know how far away each pixel is.
    TEXTURE
}
