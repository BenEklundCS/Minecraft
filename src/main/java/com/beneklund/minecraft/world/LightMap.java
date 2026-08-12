package com.beneklund.minecraft.world;

public final class LightMap {
    private final byte[] light;
    private final int size;

    public LightMap(int size) {
        this.size = size;
        light = new byte[size];
    }

    public int sky(int i) {
        return (light[i] >> 4) & 0x0F;
    }

    public int block(int i) {
        return light[i] & 0x0F;
    }

    public void setSky(int i, int level) {
        light[i] = (byte) ((light[i] & 0x0F) | (level << 4));
    }

    public void setBlock(int i, int level) {
        light[i] = (byte) ((light[i] & 0xF0) | (level & 0x0F));
    }

    public int size() {
        return size;
    }
}
