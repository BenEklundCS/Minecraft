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
    UNLOADING,
    ERROR;

    public boolean canTransitionTo(ChunkState next) {
        return switch (this) {
            // QUEUED_GEN for fresh chunks; QUEUED_MESH for chunks restored from disk
            // (byte[] is already populated, gen is skipped).
            case UNLOADED -> next == QUEUED_GEN || next == QUEUED_MESH;
            // only queued, no worker owns it yet, so it's safe to cancel early
            case QUEUED_GEN -> next == GENERATING || next == UNLOADING;
            // GENERATING and MESHING are the two states a worker runs in, so they're the only
            // ones a job can throw out of — the jobs bail early if the entry transition fails.
            case GENERATING -> next == QUEUED_MESH || next == ERROR;
            case QUEUED_MESH -> next == MESHING;
            case MESHING -> next == READY_TO_UPLOAD || next == ERROR;
            case READY_TO_UPLOAD -> next == UPLOADED;
            // live chunk: an edit dirties it, or it gets unloaded
            case UPLOADED -> next == DIRTY || next == UNLOADING;
            // re-enter the mesh pipeline, or unload before we get to it
            case DIRTY -> next == QUEUED_MESH || next == UNLOADING;
            case UNLOADING -> next == UNLOADED;
            case ERROR -> false;
        };
    }
}
