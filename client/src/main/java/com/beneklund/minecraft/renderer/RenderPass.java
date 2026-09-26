package com.beneklund.minecraft.renderer;

/**
 * The pass a {@link DrawCall} is drawn in. {@link Renderer} runs them in the order {@code SHADOW},
 * {@code OPAQUE}, {@code TRANSPARENT}, then {@code HUD} after post-processing.
 *
 * <p>{@code OPAQUE} draws with depth write on and blending off. {@code TRANSPARENT} follows with
 * depth write off and alpha blending, so water and glass blend against the finished opaque scene
 * and still draw behind each other.
 */
public enum RenderPass {
    OPAQUE,
    TRANSPARENT,
    HUD,
    SHADOW
}
