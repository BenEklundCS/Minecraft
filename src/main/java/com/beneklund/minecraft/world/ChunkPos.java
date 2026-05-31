package com.beneklund.minecraft.world;

import java.util.Objects;

public record ChunkPos(int x, int z) {
    // Records generate hashCode automatically, but the default uses identity-based
    // hashing for array components; explicit override keeps it value-based and stable.
    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
