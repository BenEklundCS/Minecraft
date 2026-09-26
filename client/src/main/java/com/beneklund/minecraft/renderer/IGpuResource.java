package com.beneklund.minecraft.renderer;

/** A renderer component that owns GPU objects: shaders it can rebuild and resources it frees. */
public interface IGpuResource {
    /** Recompiles this component's shaders from source, for shader hot reload. */
    void reload();

    /** Releases this component's GL objects. Main thread only, while the context is alive. */
    default void delete() {}
}
