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
        assertTrue(ChunkState.UNLOADED.canTransitionTo(ChunkState.QUEUED_MESH));
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
    void canTransitionTo_serverLifecycle_isAllowed() {
        assertTrue(ChunkState.UNLOADED.canTransitionTo(ChunkState.QUEUED_GEN));
        assertTrue(ChunkState.QUEUED_GEN.canTransitionTo(ChunkState.GENERATING));
        assertTrue(ChunkState.GENERATING.canTransitionTo(ChunkState.LIVE));
        assertTrue(ChunkState.UNLOADED.canTransitionTo(ChunkState.LIVE)); // loaded from disk
        assertTrue(ChunkState.LIVE.canTransitionTo(ChunkState.UNLOADING));
    }

    // A server chunk has no remesh to go back for; letting it into the mesh loop would strand it.
    @Test
    void canTransitionTo_liveIntoMeshLoop_isRejected() {
        assertFalse(ChunkState.LIVE.canTransitionTo(ChunkState.DIRTY));
        assertFalse(ChunkState.LIVE.canTransitionTo(ChunkState.QUEUED_MESH));
    }

    @Test
    void canTransitionTo_editWhileMeshing_isAllowed() {
        assertTrue(ChunkState.MESHING.canTransitionTo(ChunkState.DIRTY));
        assertTrue(ChunkState.READY_TO_UPLOAD.canTransitionTo(ChunkState.DIRTY));
    }

    // Generation and meshing run on different sides now; nothing generates and then meshes.
    @Test
    void canTransitionTo_generatedIntoMeshLoop_isRejected() {
        assertFalse(ChunkState.GENERATING.canTransitionTo(ChunkState.QUEUED_MESH));
    }

    @Test
    void canTransitionTo_unloadDuringWork_isRejected() {
        assertFalse(ChunkState.GENERATING.canTransitionTo(ChunkState.UNLOADING));
        assertFalse(ChunkState.MESHING.canTransitionTo(ChunkState.UNLOADING));
    }
}
