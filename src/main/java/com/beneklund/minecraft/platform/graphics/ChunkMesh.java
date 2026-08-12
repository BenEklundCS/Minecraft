package com.beneklund.minecraft.platform.graphics;

// Holds the VAO/VBO/EBO for one uploaded mesh. Must only be created and deleted
// on the main (GL) thread. Workers produce ChunkMeshData; this class is the GL result.
public class ChunkMesh extends Mesh {
    public ChunkMesh(Geometry geometry) {
        super(geometry, VertexFormat.CHUNK, PrimitiveMode.TRIANGLES);
        validate();
    }

    private void validate() {
        if (!Thread.currentThread().getName().equals("main"))
            throw new IllegalStateException("GpuMesh must be created on the main thread, was: %s"
                    .formatted(Thread.currentThread().getName()));
    }
}
