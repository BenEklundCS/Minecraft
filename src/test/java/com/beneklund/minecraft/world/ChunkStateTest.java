package com.beneklund.minecraft.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChunkStateTest {
    @Test
    void canTransitionTo_uploadedToQueuedGen_isRejected() {
        assertFalse(ChunkState.UPLOADED.canTransitionTo(ChunkState.QUEUED_GEN));
    }

    @Test
    void canTransitionTo_happyPath_isAllowed() {
        assertTrue(ChunkState.UNLOADED.canTransitionTo(ChunkState.QUEUED_GEN));
        assertTrue(ChunkState.QUEUED_GEN.canTransitionTo(ChunkState.GENERATING));
        assertTrue(ChunkState.GENERATING.canTransitionTo(ChunkState.QUEUED_MESH));
        assertTrue(ChunkState.QUEUED_MESH.canTransitionTo(ChunkState.MESHING));
        assertTrue(ChunkState.MESHING.canTransitionTo(ChunkState.READY_TO_UPLOAD));
        assertTrue(ChunkState.READY_TO_UPLOAD.canTransitionTo(ChunkState.UPLOADED));
    }

    @Test
    void canTransitionTo_editLoop_isAllowed() {
        assertTrue(ChunkState.UPLOADED.canTransitionTo(ChunkState.DIRTY));
        assertTrue(ChunkState.DIRTY.canTransitionTo(ChunkState.QUEUED_MESH));
    }

    @Test
    void canTransitionTo_unloadDuringWork_isRejected() {
        assertFalse(ChunkState.GENERATING.canTransitionTo(ChunkState.UNLOADING));
        assertFalse(ChunkState.MESHING.canTransitionTo(ChunkState.UNLOADING));
    }
}
