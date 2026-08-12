package com.beneklund.minecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.ChunkStore;
import com.beneklund.minecraft.player.IPhysicsBody;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.util.AABB;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.ChunkState;
import com.beneklund.minecraft.world.LocalWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.gen.IGenerationSpec;
import com.beneklund.minecraft.world.gen.WorldGenerator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

// Headless repro of Game.run()'s spawn sequence (chunkManager.tick -> physicsReady ->
// physics.update), exactly as wired in GameContainer, minus window/renderer. Goal: find
// out whether the existing physicsReady() guard actually prevents falling through an
// ungenerated column at spawn, under realistic (fixed 1/60s) frame timing.
class FallThroughReproTest {

    private static final class FakeBody implements IPhysicsBody {
        private final Vector3f position;
        private final Vector3f velocity = new Vector3f();
        private boolean onGround;

        FakeBody(Vector3f position) {
            this.position = position;
        }

        public Vector3f getPosition() {
            return position;
        }

        public Vector3f getVelocity() {
            return velocity;
        }

        public AABB getBoundingBox() {
            return AABB.ofSize(position, 0.6f, 1.6f, 0.6f);
        }

        public void setPosition(Vector3f p) {
            position.set(p);
        }

        // Physics never reads orientation, so these tests don't track it.
        public void setOrientation(float pitch, float yaw) {}

        public void setVelocity(Vector3f v) {
            velocity.set(v);
        }

        public boolean isOnGround() {
            return onGround;
        }

        public void setOnGround(boolean onGround) {
            this.onGround = onGround;
        }

        ChunkPos chunkPos() {
            return new ChunkPos(
                    Math.floorDiv((int) position.x, Chunk.SIZE_XZ), Math.floorDiv((int) position.z, Chunk.SIZE_XZ));
        }
    }

    private boolean physicsReady(World world, FakeBody body) {
        Chunk chunk = world.getChunk(body.chunkPos());
        if (chunk == null) return false;
        return switch (chunk.getState()) {
            case ChunkState.UNLOADED, ChunkState.QUEUED_GEN, ChunkState.GENERATING -> false;
            default -> true;
        };
    }

    @Test
    void spawn_fall_manyTrials_checkAgainstRealGroundHeight() throws InterruptedException {
        BlockRegistry registry = BlockRegistry.createDefault();
        long seed = 42L;
        int worldX = 8;
        int worldZ = -5;

        WorldGenerator probeGen = new WorldGenerator(registry, IGenerationSpec.DEFAULT_WORLD_GENERATION);
        Chunk probeChunk = new Chunk();
        ChunkPos probePos = new ChunkPos(Math.floorDiv(worldX, Chunk.SIZE_XZ), Math.floorDiv(worldZ, Chunk.SIZE_XZ));
        probeGen.generate(probePos, seed, probeChunk);
        int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        int trueSurfaceY = 0;
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            Block id = probeChunk.getBlock(localX, y, localZ);
            if (id != Block.AIR && registry.get(id).solid()) {
                trueSurfaceY = y;
                break;
            }
        }
        System.out.println("True generated surface Y for (" + worldX + "," + worldZ + ") = " + trueSurfaceY);

        int trials = 50;
        int fellThrough = 0;
        int wrongLanding = 0;

        for (int trial = 0; trial < trials; trial++) {
            World world = new World(new ConcurrentHashMap<>());
            LocalWorldAuthority authority = new LocalWorldAuthority(world, registry);
            WorldGenerator worldGen = new WorldGenerator(registry, IGenerationSpec.DEFAULT_WORLD_GENERATION);
            ChunkMesher mesher = new ChunkMesher(registry, null);
            WorldConfig config = new WorldConfig(seed, 4);
            ChunkStore chunkStore = new ChunkStore(seed) {
                @Override
                public void save(ChunkPos pos, Chunk chunk) {}

                @Override
                public Optional<Chunk> load(ChunkPos pos) {
                    return Optional.empty();
                }
            };
            ChunkManager chunkManager = new ChunkManager(config, world, worldGen, mesher, authority, chunkStore);

            FakeBody body = new FakeBody(new Vector3f(worldX, trueSurfaceY + 12, worldZ));
            Physics physics = new Physics();

            float dt = 1f / 60f;
            int frame = 0;
            int maxFrames = 6000; // 100 simulated seconds, generous upper bound
            boolean landed = false;
            while (frame < maxFrames) {
                chunkManager.tick(body.chunkPos());
                // Game.run() drains these every frame too — without this, meshed chunk
                // vertex/index arrays pile up unboundedly in the upload queue and blow
                // the test heap (441 chunks/trial under the hardcoded load radius).
                chunkManager.drainUploadQueue(512);
                chunkManager.drainUnloadQueue();
                if (physicsReady(world, body)) {
                    physics.update(body, authority, dt, false);
                    if (body.isOnGround()) {
                        landed = true;
                        break;
                    }
                }
                frame++;
            }

            chunkManager.shutdown(60);
            // Generation/meshing for the rest of the 441-chunk radius keeps running in the
            // background after the player lands and the loop above stops draining — flush
            // it now so those mesh arrays don't outlive this trial's scope.
            while (!chunkManager.drainUploadQueue(512).isEmpty()) {}
            chunkManager.drainUnloadQueue();

            if (!landed) {
                fellThrough++;
                System.out.println(
                        "Trial " + trial + ": NEVER LANDED after " + maxFrames + " frames, y=" + body.getPosition().y);
            } else if (Math.abs(body.getPosition().y - (trueSurfaceY + 1)) > 0.01f) {
                // resolveY rests the feet one block above the solid block's index.
                wrongLanding++;
                System.out.println(
                        "Trial " + trial + ": landed at y=" + body.getPosition().y + " expected " + (trueSurfaceY + 1));
            }
        }

        System.out.println("fellThrough=" + fellThrough + " wrongLanding=" + wrongLanding + " / " + trials);

        // Was a probe wearing @Test — it printed these counts and passed no matter what they were.
        assertEquals(0, fellThrough, "trials that never landed, out of " + trials);
        assertEquals(0, wrongLanding, "trials that landed at the wrong height, out of " + trials);
    }
}
