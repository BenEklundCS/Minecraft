package com.beneklund.minecraft.infra;

import com.beneklund.minecraft.world.IChunkStore;

// Concrete in-memory backing store for chunks. Abstract so a disk-backed or
// network-backed subclass can slot in behind the same IChunkStore interface.
public abstract class ChunkStore implements IChunkStore {}
