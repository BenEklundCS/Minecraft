package com.beneklund.minecraft.platform.graphics;

import java.util.List;

public class VertexFormat {
    public static final VertexFormat CHUNK = new VertexFormat(List.of(
            new VertexAttribute(0, 3), // position xyz
            new VertexAttribute(1, 2), // uv
            new VertexAttribute(2, 1), // ao
            new VertexAttribute(3, 1), // faceId
            new VertexAttribute(4, 3))); // tint rgb

    public static final VertexFormat HUD = new VertexFormat(List.of(
            new VertexAttribute(0, 2), // aPos   vec2
            new VertexAttribute(1, 4), // aColor vec4
            new VertexAttribute(2, 2))); // aUV    vec2

    public static final VertexFormat LINE = new VertexFormat(List.of(
            new VertexAttribute(0, 3), // position xyz
            new VertexAttribute(1, 3))); // color    rgb

    List<VertexAttribute> attributes;

    public List<VertexAttribute> attributes() {
        return attributes;
    }

    public record VertexAttribute(int location, int components) {}
    ;

    public VertexFormat(List<VertexAttribute> attributes) {
        this.attributes = attributes;
    }

    public void describe(GlVertexArray glVertexArray) {
        List<VertexFormat.VertexAttribute> attributes = attributes();
        for (int i = 0; i < attributes.size(); i++) {
            VertexFormat.VertexAttribute a = attributes.get(i);
            glVertexArray.attribPointer(a.location(), a.components(), stride(), offsetOf(i));
        }
    }

    public void checkVertexCount(int floats) {
        if (floats % floatsPerVertex() != 0) throw new IllegalArgumentException();
    }

    public int floatsPerVertex() {
        return sumOfComponents();
    }

    public int stride() {
        return floatsPerVertex() * Float.BYTES;
    }

    public long offsetOf(int i) {
        return (long) Float.BYTES * sumOfComponents(i);
    }

    private int sumOfComponents() {
        return attributes.stream()
                .map((vertexAttribute -> vertexAttribute.components))
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int sumOfComponents(int i) {
        return attributes.stream()
                .limit(i)
                .map((vertexAttribute -> vertexAttribute.components))
                .mapToInt(Integer::intValue)
                .sum();
    }
}
