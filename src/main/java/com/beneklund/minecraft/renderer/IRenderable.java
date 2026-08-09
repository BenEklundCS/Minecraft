package com.beneklund.minecraft.renderer;

import java.util.List;

public interface IRenderable {
    List<DrawCall> getDrawCalls(Camera camera);

    void reload();

    default void delete() {}
}
