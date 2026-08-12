package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;

import java.util.List;

public final class VertexFormat {
    public static final VertexFormat CHUNK = new VertexFormat(List.of(
            new VertexAttribute(AttributeType.VEC3), // position xyz
            new VertexAttribute(AttributeType.VEC2), // uv
            new VertexAttribute(AttributeType.FLOAT), // ao
            new VertexAttribute(AttributeType.FLOAT), // faceId
            new VertexAttribute(AttributeType.VEC3), // tint rgb
            new VertexAttribute(AttributeType.VEC2))); // light: (sky, block), each 0..1

    public static final VertexFormat HUD = new VertexFormat(List.of(
            new VertexAttribute(AttributeType.VEC2), // aPos   vec2
            new VertexAttribute(AttributeType.VEC4), // aColor vec4
            new VertexAttribute(AttributeType.VEC2))); // aUV  vec2

    public static final VertexFormat LINE = new VertexFormat(List.of(
            new VertexAttribute(AttributeType.VEC3), // position xyz
            new VertexAttribute(AttributeType.VEC3))); // color  rgb

    public VertexFormat(List<VertexAttribute> attributes) {
        this.attributes = attributes;
    }

    public record VertexAttribute(AttributeType type) {}

    public void describe(GlVertexArray va) {
        List<VertexFormat.VertexAttribute> attributes = attributes();
        for (int i = 0; i < attributes.size(); i++) {
            VertexFormat.AttributeType a = attributes.get(i).type();
            va.attribPointer(i, a.size(), a.glType, a.normalized, stride(), offsetOf(i));
        }
    }

    public void checkVertexCount(int floats) {
        if (floats % floatsPerVertex() != 0)
            throw new IllegalArgumentException("float count: %d expect: %d".formatted(floats, floatsPerVertex()));
    }

    private final List<VertexAttribute> attributes;

    private List<VertexAttribute> attributes() {
        return attributes;
    }

    private enum AttributeType {
        FLOAT(1),
        VEC2(2),
        VEC3(3),
        VEC4(4);

        private final int components;
        private final int glType;
        private final boolean normalized;
        private final int bytesPerComponent;

        AttributeType(int components) {
            this.components = components;
            glType = GL_FLOAT;
            normalized = false;
            bytesPerComponent = Float.BYTES;
        }

        AttributeType(int components, int type, boolean normalized, int bytesPerComponent) {
            this.components = components;
            glType = type;
            this.normalized = normalized;
            this.bytesPerComponent = bytesPerComponent;
        }

        public int size() {
            return components;
        }

        public int bytes() {
            return components * bytesPerComponent;
        }
    }

    public int floatsPerVertex() {
        return sumOfComponents();
    }

    int stride() {
        return sumOfBytes(attributes.size());
    }

    long offsetOf(int i) {
        return sumOfBytes(i);
    }

    private int sumOfComponents() {
        return attributes.stream()
                .map((vertexAttribute -> vertexAttribute.type().components))
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int sumOfBytes(int count) {
        return attributes.stream().limit(count).mapToInt(a -> a.type().bytes()).sum();
    }
}
