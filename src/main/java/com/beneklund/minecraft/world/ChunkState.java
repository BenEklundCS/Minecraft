package com.beneklund.minecraft.world;

public enum ChunkState {
    UNLOADED,
    QUEUED_GEN,
    GENERATING,
    QUEUED_MESH,
    MESHING,
    READY_TO_UPLOAD,
    UPLOADED,
    DIRTY,
    UNLOADING;

    public boolean canTransitionTo(ChunkState next) {
        return switch (this) {
            case UNLOADED -> next == QUEUED_GEN;
            // only queued, no worker owns it yet, so it's safe to cancel early
            case QUEUED_GEN -> next == GENERATING || next == UNLOADING;
            case GENERATING -> next == QUEUED_MESH;
            case QUEUED_MESH -> next == MESHING;
            case MESHING -> next == READY_TO_UPLOAD;
            case READY_TO_UPLOAD -> next == UPLOADED;
            // live chunk: an edit dirties it, or it gets unloaded
            case UPLOADED -> next == DIRTY || next == UNLOADING;
            // re-enter the mesh pipeline, or unload before we get to it
            case DIRTY -> next == QUEUED_MESH || next == UNLOADING;
            case UNLOADING -> next == UNLOADED;
        };
    }
}
