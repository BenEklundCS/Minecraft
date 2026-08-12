package com.beneklund.minecraft.renderer;

import java.util.Arrays;

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

    // Grow before writing one more quad's worth of vertices/indices.
    public void ensureQuadCapacity() {
        if (vertPos + verticesPerQuad * floatsPerVertex > vertices.length)
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        if (idxPos + indicesPerQuad > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
    }

    public int base() {
        return vertexBase;
    }

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
