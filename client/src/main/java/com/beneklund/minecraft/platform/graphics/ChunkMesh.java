package com.beneklund.minecraft.platform.graphics;

/**
 * The GPU half of one chunk's mesh, in {@link VertexFormat#CHUNK} layout.
 *
 * <p>Meshing workers build {@code ChunkMeshData}, plain arrays with no GL state; the main thread
 * uploads it into this. The constructor checks the thread name and throws anywhere but {@code
 * main}, because the main thread holds the only GL context.
 */
public class ChunkMesh extends Mesh {
    /** @throws IllegalStateException if called off the main thread */
    public ChunkMesh(Geometry geometry) {
        super(geometry, VertexFormat.CHUNK, PrimitiveMode.TRIANGLES);
        validate();
    }

    private void validate() {
        if (!Thread.currentThread().getName().equals("main"))
            throw new IllegalStateException("ChunkMesh must be created on the main thread, was: %s"
                    .formatted(Thread.currentThread().getName()));
    }
}
