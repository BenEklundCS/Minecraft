package com.beneklund.minecraft.world;

import java.util.Objects;

public record ChunkPos(int x, int z) {
    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
