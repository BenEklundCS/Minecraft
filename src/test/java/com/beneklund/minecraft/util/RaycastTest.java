package com.beneklund.minecraft.util;

import static org.junit.jupiter.api.Assertions.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.entity.Entity;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.IWorldAuthority;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

class RaycastTest {
    private static final BlockDef STONE = new BlockDef(true, false, new String[0]);
    private static final BlockDef AIR = new BlockDef(false, true, new String[0]);
    private static final float REACH = 8.0f;

    // A world that is solid exactly at the given block coordinates, AIR everywhere else.
    // Only getBlock is exercised by the raycaster.
    private static IWorldAuthority worldWith(Vector3i... solidBlocks) {
        Set<Vector3i> solid = new HashSet<>(List.of(solidBlocks));
        return new IWorldAuthority() {
            public BlockDef getBlock(int x, int y, int z) {
                return solid.contains(new Vector3i(x, y, z)) ? STONE : AIR;
            }

            public void setBlock(int x, int y, int z, Block block) {}

            public Chunk getChunk(ChunkPos pos) {
                return null;
            }

            public List<Entity> getEntities(AABB aabb) {
                return List.of();
            }

            public void markCardinalNeighborsDirty(ChunkPos pos) {}
        };
    }

    // 16.T1 — looking straight down +Z at a block one unit ahead hits its near (NORTH) face at distance 1.
    @Test
    void cast_hitsBlockAhead_reportsNorthFaceAtDistanceOne() {
        IWorldAuthority world = worldWith(new Vector3i(0, 64, 0));

        RaycastResult result = Raycast.cast(new Vector3f(0.5f, 64.5f, -1.0f), new Vector3f(0, 0, 1), world, REACH);

        assertTrue(result.hit(), "ray should hit the block directly ahead");
        assertEquals(new Vector3i(0, 64, 0), result.blockPos(), "should report the struck block");
        assertEquals(Direction.NORTH, result.hitFace(), "entered from -Z, so the NORTH face was struck");
        assertEquals(1.0f, result.distance(), 1e-4, "block boundary sits one unit from the origin");
    }

    // 16.T2 — a ray through empty space within reach returns a miss.
    @Test
    void cast_emptyWorld_returnsMiss() {
        IWorldAuthority world = worldWith(); // nothing solid anywhere

        RaycastResult result = Raycast.cast(new Vector3f(0.5f, 64.5f, -1.0f), new Vector3f(0, 0, 1), world, REACH);

        assertFalse(result.hit(), "no solid block along the ray means no hit");
    }

    // 16.T3 — with two solid blocks along the ray, the nearer one is returned, not the far one behind it.
    @Test
    void cast_stopsAtNearestBlock_notTheOneBehind() {
        Vector3i near = new Vector3i(0, 64, 0);
        Vector3i far = new Vector3i(0, 64, 3);
        IWorldAuthority world = worldWith(near, far);

        RaycastResult result = Raycast.cast(new Vector3f(0.5f, 64.5f, -1.0f), new Vector3f(0, 0, 1), world, REACH);

        assertTrue(result.hit());
        assertEquals(near, result.blockPos(), "should stop at the nearest block");
        assertNotEquals(far, result.blockPos(), "must not pass through to the block behind");
        assertEquals(1.0f, result.distance(), 1e-4, "distance is to the near block, not the far one");
    }
}
