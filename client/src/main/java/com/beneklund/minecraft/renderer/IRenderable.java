package com.beneklund.minecraft.renderer;

import java.util.List;

/**
 * A renderer subsystem that describes its geometry as {@link DrawCall}s and lets {@link Renderer}
 * issue them.
 */
public interface IRenderable extends IGpuResource {
    /**
     * The draws for this frame, seen from {@code camera}. {@link Renderer} sorts and issues them,
     * so an implementation leaves GL state alone here.
     */
    List<DrawCall> getDrawCalls(Camera camera);
}
