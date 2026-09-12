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
    LIVE,
    ERROR;

    public boolean canTransitionTo(ChunkState next) {
        return switch (this) {
            // QUEUED_GEN for fresh chunks. A chunk restored from disk already has its blocks, so it
            // skips generation: QUEUED_MESH on the client, LIVE on the server.
            case UNLOADED -> next == QUEUED_GEN || next == QUEUED_MESH || next == LIVE;
            // only queued, no worker owns it yet, so it's safe to cancel early
            case QUEUED_GEN -> next == GENERATING || next == UNLOADING;
            // GENERATING and MESHING are the two states a worker runs in, so they're the only
            // ones a job can throw out of — the jobs bail early if the entry transition fails.
            case GENERATING -> next == LIVE || next == ERROR;
            // Nothing to re-enter: edits replicate as BlockChanged and persist via needsPersisting,
            // so the only way out is eviction.
            case LIVE -> next == UNLOADING;
            case QUEUED_MESH -> next == MESHING;
            // DIRTY mid-mesh: the job's READY_TO_UPLOAD then fails and tick() meshes it again, so an
            // edit that lands while a mesh is in flight isn't lost.
            case MESHING -> next == READY_TO_UPLOAD || next == DIRTY || next == ERROR;
            // Same for an edit after meshing: the queued mesh still uploads, then tick() remeshes.
            case READY_TO_UPLOAD -> next == UPLOADED || next == DIRTY;
            // on screen: an edit dirties it, or it gets unloaded
            case UPLOADED -> next == DIRTY || next == UNLOADING;
            // re-enter the mesh pipeline, or unload before we get to it
            case DIRTY -> next == QUEUED_MESH || next == UNLOADING;
            case UNLOADING -> next == UNLOADED;
            case ERROR -> false;
        };
    }
}
