package com.beneklund.minecraft.renderer.chunk;

import java.util.Arrays;

/**
 * A growable vertex and index buffer that {@link ChunkMesher} fills one quad at a time.
 *
 * <p>Per quad: {@link #ensureQuadCapacity()}, {@link #writeVert} for every float of every vertex,
 * {@link #writeIdx} with indices offset by {@link #base()}, then {@link #advance()}. The arrays
 * double when a quad won't fit, and {@link #copyVertices()} and {@link #copyIndices()} trim to what
 * was written.
 */
public final class ChunkMeshingBuffer {
    private final int initialFaceCapacity;
    private final int verticesPerQuad;
    private final int floatsPerVertex;
    private final int indicesPerQuad;

    private int vertPos = 0;
    private int quadStart = 0;
    private int idxPos = 0;
    private int vertexBase = 0;

    private float[] vertices;
    private int[] indices;

    public ChunkMeshingBuffer(int initialFaceCapacity, int verticesPerQuad, int floatsPerVertex, int indicesPerQuad) {
        this.initialFaceCapacity = initialFaceCapacity;
        this.verticesPerQuad = verticesPerQuad;
        this.floatsPerVertex = floatsPerVertex;
        this.indicesPerQuad = indicesPerQuad;
        vertices = emptyVertices();
        indices = emptyIndices();
    }

    /** Doubles either array that can't hold one more quad. Call before writing each quad. */
    public void ensureQuadCapacity() {
        if (vertPos + verticesPerQuad * floatsPerVertex > vertices.length)
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        if (idxPos + indicesPerQuad > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
    }

    /** Index of the current quad's first vertex. After meshing, the total vertex count. */
    public int base() {
        return vertexBase;
    }

    /**
     * Closes the current quad.
     *
     * @throws IllegalStateException if the quad wrote a different number of floats than one quad
     *     of the vertex format holds, which means the mesher and {@code VertexFormat.CHUNK} disagree
     */
    public void advance() {
        int actual = vertPos - quadStart;
        int expect = getQuadVertexFloats();
        if (actual == expect) {
            quadStart = vertPos;
            vertexBase += verticesPerQuad;
        } else {
            throw new IllegalStateException("expected: %d actual: %d".formatted(expect, actual));
        }
    }

    public void writeVert(float v) {
        vertices[vertPos++] = v;
    }

    public void writeIdx(int i) {
        indices[idxPos++] = i;
    }

    public float[] copyVertices() {
        return Arrays.copyOf(vertices, vertPos);
    }

    public int[] copyIndices() {
        return Arrays.copyOf(indices, idxPos);
    }

    private int getQuadVertexFloats() {
        return verticesPerQuad * floatsPerVertex;
    }

    private float[] emptyVertices() {
        return new float[initialFaceCapacity * getQuadVertexFloats()];
    }

    private int[] emptyIndices() {
        return new int[initialFaceCapacity * indicesPerQuad];
    }
}
