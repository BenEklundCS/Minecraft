package com.beneklund.minecraft.renderer;

// Which pass a DrawCall belongs to. The Renderer draws all OPAQUE calls first (depth write on,
// blending off), then all TRANSPARENT calls (depth write off, alpha blending) so transparent
// surfaces blend against the opaque geometry already in the framebuffer.
public enum RenderPass {
    OPAQUE,
    TRANSPARENT
}
