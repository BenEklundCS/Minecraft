package com.beneklund.minecraft.container;

import com.beneklund.minecraft.Game;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.infra.ChunkManager;
import com.beneklund.minecraft.infra.RenderWorld;
import com.beneklund.minecraft.input.InputHandler;
import com.beneklund.minecraft.platform.audio.AudioPlayer;
import com.beneklund.minecraft.platform.audio.StbIAudioLoader;
import com.beneklund.minecraft.platform.images.StbIImageLoader;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.InputMapper;
import com.beneklund.minecraft.platform.resources.JsonIResourcePack;
import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.player.Player;
import com.beneklund.minecraft.renderer.Camera;
import com.beneklund.minecraft.renderer.ChunkMesher;
import com.beneklund.minecraft.renderer.ChunkRenderer;
import com.beneklund.minecraft.renderer.TextureAtlas;
import com.beneklund.minecraft.util.Color;
import com.beneklund.minecraft.util.DeltaTracker;
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
        WindowConfig config = new WindowConfig("Minecraft", 1920, 1080, false, Color.SKY);

        // 2. Input plumbing - pure Java, no GLFW/GL yet.
        InputEventQueue queue = new InputEventQueue();
        InputMapper mapper = new InputMapper(queue);

        // 3. Pre-init platform objects - constructed but not yet active.
        CameraConfig cameraConfig = new CameraConfig(new Vector3f(8.0f, 75.0f, -5.0f), 20.0f, 70.0f);
        PlayerConfig playerConfig = new PlayerConfig(5.0f);
        Player player = new Player(cameraConfig.startPosition(), playerConfig.movementSpeed());
        player.look(0, cameraConfig.startPitch());
        Camera camera = new Camera(config, player, cameraConfig.fov());
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
        ChunkRenderer chunkRenderer = new ChunkRenderer(atlas);
        RenderWorld renderWorld = new RenderWorld();

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
        new Game(window, chunkRenderer, chunkManager, renderWorld, camera, player, world, delta, mapper).run();

        // 8. Shutdown - reverse dependency order: audio before window (AL before GLFW/GL).
        music.shutdown();
        chunkRenderer.delete();
        atlas.delete();
        window.shutdown();
    }
}
