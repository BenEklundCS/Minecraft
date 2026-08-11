package com.beneklund.minecraft.renderer;

import java.util.Arrays;

public final class ChunkMeshingBuffer {
    private final int initialFaceCapacity;
    private final int verticesPerQuad;
    private final int floatsPerVertex;
    private final int indicesPerQuad;

    private int vertPos = 0;
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
        vertexBase += verticesPerQuad;
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

    private float[] emptyVertices() {
        return new float[initialFaceCapacity * verticesPerQuad * floatsPerVertex];
    }

    private int[] emptyIndices() {
        return new int[initialFaceCapacity * indicesPerQuad];
    }
}
