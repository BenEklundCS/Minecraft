package com.beneklund.minecraft.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.platform.graphics.Geometry;
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
            public void markNeighborsDirty(ChunkPos pos) {}
        };

        // Stub mesher — returns an empty but valid ChunkMeshData so the upload queue receives an item.
        // The signature has to match ChunkMesher.mesh(ChunkPos, ChunkWithNeighbors) exactly; an
        // earlier version took (ChunkPos, Chunk), overrode nothing, and silently let this test
        // drive the real mesher. @Override is what keeps that from happening again.
        ChunkMesher stubMesher = new ChunkMesher(BlockRegistry.createDefault(), null) {
            @Override
            public ChunkMeshData mesh(ChunkPos pos, ChunkWithNeighbors cn) {
                return new ChunkMeshData(pos, Geometry.EMPTY, Geometry.EMPTY, 0, cn.center());
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

        World world = new World(new ConcurrentHashMap<>());
        WorldConfig config = new WorldConfig(42L, 4);
        LightEngine lightEngine = new LightEngine(BlockRegistry.createDefault());
        ChunkManager manager =
                new ChunkManager(config, world, stubGen, stubMesher, stubAuthority, stubChunkStore, lightEngine);

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

    // getChunksInRadius touches no collaborator — it's pure spiral arithmetic over its two
    // arguments — and the constructor only stores its dependencies. The one place a constructor
    // argument is dereferenced is config.seed() inside genJob, which nothing here submits. So
    // nulls are safe, and the cache tests skip building a BlockRegistry to get a real mesher.
    private static ChunkManager cacheOnlyManager() {
        return new ChunkManager(null, null, null, null, null, null, null);
    }

    // The card's acceptance criterion. Two ChunkPos values that are equal but not the same
    // object have to hit — the old code compared with ==, which a record never satisfies across
    // separate instances, so the branch was dead even once the field was being assigned.
    @Test
    void equalButDistinctChunkPos_returnsTheSameListInstance() throws InterruptedException {
        ChunkManager manager = cacheOnlyManager();

        List<ChunkPos> first = manager.getChunksInRadius(new ChunkPos(3, 7), 2);
        List<ChunkPos> second = manager.getChunksInRadius(new ChunkPos(3, 7), 2);

        assertNotSame(new ChunkPos(3, 7), new ChunkPos(3, 7), "the two keys really are distinct objects");
        assertSame(first, second, "second call must come from the cache");
        manager.shutdown(1);
    }

    // The very first call has a null cache. Worth pinning: written the other way round
    // (cache.equals(key)) this NPEs before the field is ever assigned.
    @Test
    void firstCall_withAnEmptyCache_doesNotThrow() throws InterruptedException {
        ChunkManager manager = cacheOnlyManager();

        List<ChunkPos> result = manager.getChunksInRadius(new ChunkPos(0, 0), 1);

        assertEquals(9, result.size(), "radius 1 is a 3x3 square");
        manager.shutdown(1);
    }

    @Test
    void movingToANewChunk_recomputes() throws InterruptedException {
        ChunkManager manager = cacheOnlyManager();

        List<ChunkPos> atOrigin = manager.getChunksInRadius(new ChunkPos(0, 0), 1);
        List<ChunkPos> movedOver = manager.getChunksInRadius(new ChunkPos(1, 0), 1);

        assertNotSame(atOrigin, movedOver);
        assertTrue(movedOver.contains(new ChunkPos(2, 0)), "the new square is centred on the new position");
        assertFalse(movedOver.contains(new ChunkPos(-1, 0)), "and no longer reaches the old edge");
        manager.shutdown(1);
    }

    // The reason the key is a record over (pos, radius) rather than just the position. Keying on
    // position alone would hand back the radius-1 list for a radius-3 request.
    @Test
    void samePositionDifferentRadius_recomputes() throws InterruptedException {
        ChunkManager manager = cacheOnlyManager();

        List<ChunkPos> near = manager.getChunksInRadius(new ChunkPos(4, 4), 1);
        List<ChunkPos> far = manager.getChunksInRadius(new ChunkPos(4, 4), 3);

        assertNotSame(near, far);
        assertEquals(9, near.size());
        assertEquals(49, far.size(), "radius 3 is a 7x7 square");
        manager.shutdown(1);
    }

    // A cache is only correct if it returns what recomputing would have. Same inputs after an
    // eviction must produce an equal list, not just any list.
    @Test
    void aCacheMiss_producesAnEqualListToTheEvictedOne() throws InterruptedException {
        ChunkManager manager = cacheOnlyManager();

        List<ChunkPos> before = List.copyOf(manager.getChunksInRadius(new ChunkPos(2, 2), 2));
        manager.getChunksInRadius(new ChunkPos(9, 9), 2); // evicts
        List<ChunkPos> after = manager.getChunksInRadius(new ChunkPos(2, 2), 2);

        assertEquals(before, after, "same inputs, same square, same spiral order");
        manager.shutdown(1);
    }
}
