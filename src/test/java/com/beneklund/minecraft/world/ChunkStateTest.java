package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChunkStateTest {
    // an illegal jump (a live chunk going back to "queued for generation") is rejected
    @Test
    void canTransitionTo_uploadedToQueuedGen_isRejected() {
        assertFalse(ChunkState.UPLOADED.canTransitionTo(ChunkState.QUEUED_GEN));
    }

    // every hand-off on the generate -> mesh -> upload happy path is allowed
    @Test
    void canTransitionTo_happyPath_isAllowed() {
        assertTrue(ChunkState.UNLOADED.canTransitionTo(ChunkState.QUEUED_GEN));
        assertTrue(ChunkState.QUEUED_GEN.canTransitionTo(ChunkState.GENERATING));
        assertTrue(ChunkState.GENERATING.canTransitionTo(ChunkState.QUEUED_MESH));
        assertTrue(ChunkState.QUEUED_MESH.canTransitionTo(ChunkState.MESHING));
        assertTrue(ChunkState.MESHING.canTransitionTo(ChunkState.READY_TO_UPLOAD));
        assertTrue(ChunkState.READY_TO_UPLOAD.canTransitionTo(ChunkState.UPLOADED));
    }

    // an edit dirties a live chunk, which re-enters the mesh pipeline
    @Test
    void canTransitionTo_editLoop_isAllowed() {
        assertTrue(ChunkState.UPLOADED.canTransitionTo(ChunkState.DIRTY));
        assertTrue(ChunkState.DIRTY.canTransitionTo(ChunkState.QUEUED_MESH));
    }

    // a worker owns the chunk mid generation/mesh, so it can't be yanked to UNLOADING
    @Test
    void canTransitionTo_unloadDuringWork_isRejected() {
        assertFalse(ChunkState.GENERATING.canTransitionTo(ChunkState.UNLOADING));
        assertFalse(ChunkState.MESHING.canTransitionTo(ChunkState.UNLOADING));
    }
}
