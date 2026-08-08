package com.beneklund.minecraft.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.renderer.ChunkMeshData;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.*;
import com.beneklund.minecraft.world.gen.IWorldGenerator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ChunkManagerTest {

    // States that count as "pipeline complete" — anything at or past READY_TO_UPLOAD.
    private static final Set<ChunkState> DONE_STATES =
            EnumSet.of(ChunkState.READY_TO_UPLOAD, ChunkState.UPLOADED, ChunkState.DIRTY);

    @Test
    void allChunksReachReadyToUpload_after_shutdown() throws InterruptedException {
        // Stub generator — fills nothing, just lets the state machine advance.
        IWorldGenerator stubGen = (pos, seed, chunk) -> {};

        // Stub authority — no neighbor dirtying needed for this test.
        IWorldAuthority stubAuthority = new IWorldAuthority() {
            @Override
            public BlockDef getBlock(int x, int y, int z) {
                return null;
            }

            @Override
            public void setBlock(int x, int y, int z, Block block) {}

            @Override
            public Chunk getChunk(ChunkPos pos) {
                return null;
            }

            @Override
            public List<Entity> getEntities(AABB aabb) {
                return List.of();
            }

            @Override
            public void markCardinalNeighborsDirty(ChunkPos pos) {}
        };

        // Stub mesher — returns an empty but valid ChunkMeshData so the upload queue receives an item.
        // The signature has to match ChunkMesher.mesh(ChunkPos, ChunkWithNeighbors) exactly; an
        // earlier version took (ChunkPos, Chunk), overrode nothing, and silently let this test
        // drive the real mesher. @Override is what keeps that from happening again.
        ChunkMesher stubMesher = new ChunkMesher(BlockRegistry.createDefault(), null) {
            @Override
            public ChunkMeshData mesh(ChunkPos pos, ChunkMesher.ChunkWithNeighbors cn) {
                return new ChunkMeshData(pos, new float[0], new int[0], new float[0], new int[0], 0, cn.chunk());
            }
        };

        long seed = 42L;
        ChunkStore stubChunkStore = new ChunkStore(seed) {
            @Override
            public void save(ChunkPos pos, Chunk chunk) {}

            @Override
            public Optional<Chunk> load(ChunkPos pos) {
                return Optional.empty();
            }
        };

        World world = new World(new ConcurrentHashMap<>(), null);
        WorldConfig config = new WorldConfig(42L, 4);
        ChunkManager manager = new ChunkManager(config, world, stubGen, stubMesher, stubAuthority, stubChunkStore);

        // Tick with 10 distinct player positions — each adds new chunks within radius 4 of that position.
        for (int i = 0; i < 10; i++) {
            manager.tick(new ChunkPos(i, 0));
        }

        // Give workers up to 10 seconds to drain the gen and mesh pipelines.
        manager.shutdown(10);

        // Every chunk in the world must have reached READY_TO_UPLOAD or later.
        Set<ChunkPos> positions = world.getChunkPositions();
        assertFalse(positions.isEmpty(), "world should contain chunks after tick");
        for (ChunkPos pos : positions) {
            Chunk chunk = world.getChunk(pos);
            assertNotNull(chunk, "chunk at " + pos + " must still be in world");
            assertTrue(
                    DONE_STATES.contains(chunk.getState()), "chunk at " + pos + " stuck in state " + chunk.getState());
        }
    }
}
