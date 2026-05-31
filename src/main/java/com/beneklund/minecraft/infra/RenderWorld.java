package com.beneklund.minecraft.infra;

// Adapter that gives the renderer a read-only view of loaded chunks without
// leaking ChunkManager internals into the rendering layer. Keeps the renderer
// ignorant of how chunks are stored or scheduled.
public class RenderWorld {}
