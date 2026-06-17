package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.ChunkRenderable;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.audio.StbIAudioLoader;
import com.beneklund.minecraft.platform.images.StbIImageLoader;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.resources.JsonIResourcePack;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Physics;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.renderer.DebugRenderer;
import com.beneklund.minecraft.renderer.Renderer;
import com.beneklund.minecraft.renderer.TextureAtlas;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.DeltaTracker;
import com.beneklund.minecraft.world.Chunk;
import com.beneklund.minecraft.world.ChunkPos;
import com.beneklund.minecraft.world.LocalWorldAuthority;
import com.beneklund.minecraft.world.World;
import com.beneklund.minecraft.world.WorldConfig;
import com.beneklund.minecraft.world.gen.IGenerationSpec;
import com.beneklund.minecraft.world.gen.WorldGenerator;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;

// Composition root — the only place that wires concrete types together.
// Nothing outside this class should call `new` on platform or renderer objects.
public class GameContainer {
    public void run() throws IOException {
        // 1. Config - pure data, no platform deps.
        LocalConfig localConfig = new LocalConfig();
        WindowConfig config = new WindowConfig("Minecraft", 1200, 800, false, Color.SKY, localConfig.debugEnabled());

        // 2. Input plumbing - pure Java, no GLFW/GL yet.
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);

        // 3. Pre-init platform objects - constructed but not yet active.
        CameraConfig cameraConfig = new CameraConfig(70.0f);
        PlayerConfig playerConfig = new PlayerConfig(new Vector3f(8.0f, 75.0f, -5.0f), 20.0f, 4.3f, 8.4f, 8.0f);
        Camera camera = new Camera(config, cameraConfig);
        Window window = new Window(config, queue);
        InputHandler handler = new InputHandler(window, camera);
        DeltaTracker delta = new DeltaTracker(window::getTime);
        window.addResizeListener(camera::setWindowSize);

        // 4. window.init() - creates the GLFW window and makes the GL context current.
        //    Nothing that calls GL or uploads to the GPU may run before this line.
        window.init();

        // 5. GL resources - shaders, VAOs, textures. Requires active GL context.
        JsonIResourcePack resourcePack = new JsonIResourcePack("/packs/faithful/pack.json", new StbIImageLoader());
        TextureAtlas atlas = new TextureAtlas(resourcePack);
        BlockRegistry registry = BlockRegistry.createDefault();
        RenderWorld renderWorld = new RenderWorld();
        ChunkRenderable chunkRenderable = new ChunkRenderable(renderWorld, atlas);
        DebugRenderer debugRenderer = new DebugRenderer();
        Renderer renderer = new Renderer(List.of(chunkRenderable, debugRenderer));

        // 6. Audio - OpenAL is lazy-initialized on first play(), but construct after GL
        //    so the window is confirmed healthy before we open the audio device.
        AudioPlayer music = new AudioPlayer(new StbIAudioLoader());
        localConfig.startupDisc().ifPresent(music::play);

        // 7. Game logic - depends on input and the GL renderer being ready.
        World world = new World(new ConcurrentHashMap<>(), handler);
        LocalWorldAuthority authority = new LocalWorldAuthority(world, registry);
        List<IGenerationSpec> generationSpecs = IGenerationSpec.DEFAULT_WORLD_GENERATION;
        WorldConfig worldConfig = new WorldConfig(42L, 4);
        WorldGenerator worldGen = new WorldGenerator(registry, generationSpecs);
        ChunkMesher mesher = new ChunkMesher(registry, atlas);
        ChunkManager chunkManager = new ChunkManager(worldConfig, world, worldGen, mesher, authority);

        Player player = new Player(playerConfig, camera, authority);

        // Spawn resting directly on the surface (feet one block above the highest solid).
        // We used to spawn ~12 blocks up and free-fall, but that gave gravity a chance to
        // build speed and tunnel the player through the ground during a spawn-time frame
        // hitch (the mesh/upload storm). No fall = no tunnel. The generator is deterministic,
        // so this measured height matches the async-generated chunk exactly.
        Vector3f spawnXz = playerConfig.startPosition();
        int surfaceY = surfaceHeight(
                worldGen, worldConfig.seed(), registry, (int) Math.floor(spawnXz.x), (int) Math.floor(spawnXz.z));
        player.setPosition(new Vector3f(spawnXz.x, surfaceY + 1, spawnXz.z));

        new Game(
                        window,
                        renderer,
                        chunkManager,
                        renderWorld,
                        camera,
                        player,
                        new Physics(),
                        world,
                        authority,
                        delta,
                        mapper,
                        debugRenderer)
                .run();

        // 8. Shutdown - reverse dependency order: audio before window (AL before GLFW/GL).
        music.shutdown();
        renderer.delete();
        atlas.delete();
        window.shutdown();
    }

    // Highest solid block in a world column. Generates that column's chunk and scans
    // top-down — air, water, and leaves are skipped so we land on real ground. Bedrock at
    // y=0 guarantees the loop always finds something.
    private static int surfaceHeight(
            WorldGenerator worldGen, long seed, BlockRegistry registry, int worldX, int worldZ) {
        Chunk chunk = new Chunk();
        ChunkPos pos = new ChunkPos(Math.floorDiv(worldX, Chunk.SIZE_XZ), Math.floorDiv(worldZ, Chunk.SIZE_XZ));
        worldGen.generate(pos, seed, chunk);

        int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
        int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            byte id = chunk.getBlock(localX, y, localZ);
            if (id != Block.AIR && registry.get(id).solid()) return y;
        }
        return 0;
    }
}
