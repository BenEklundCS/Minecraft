package com.beneklund.minecraft.platform.window;

import static com.beneklund.minecraft.util.Log.LOGGER;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.platform.input.RawInputEvent;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.MemoryStack;

/*
 * Owns the OS window, the OpenGL context, and the game loop boundary (beginFrame/endFrame).
 * Everything GLFW-related is confined here - nothing else in the codebase calls glfwInit
 * or touches a window handle.
 *
 * The window handle is a long because GLFW is a C library. NULL (0L) means no window.
 * GLFW_CORE_PROFILE disables the old OpenGL compatibility features we don't want -
 * if you accidentally use a deprecated API, you get an error instead of silent garbage.
 *
 * The window starts hidden (GLFW_VISIBLE = false) so it doesn't flash on screen while
 * we're still setting up. glfwShowWindow() reveals it only after everything is ready.
 *
 * GL.createCapabilities() is the LWJGL handshake - it reads what the driver supports
 * and makes the corresponding GL functions available. Nothing GL-related works before this.
 *
 * GLUtil.setupDebugMessageCallback() hooks into the GL debug extension. Without it,
 * bad GL calls silently return error codes. With it, every GL error prints immediately
 * with a full description.
 *
 * Double buffering: the game draws into a back buffer each frame. glfwSwapBuffers()
 * flips it to the screen atomically - the player never sees a half-drawn frame.
 */
public class Window {
    private long window;
    private final InputEventQueue queue;
    private final WindowConfig config;
    private final List<ResizeListener> resizeListeners = new ArrayList<>();

    public Window(WindowConfig config, InputEventQueue queue) {
        this.config = config;
        this.queue = queue;
    }

    public void init() {
        initGlfw();
        initOpenGL();
        initCallbacks();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(this.window);
    }

    public void close() {
        glfwSetWindowShouldClose(this.window, true);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void beginFrame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void endFrame() {
        glfwSwapBuffers(this.window);
    }

    public void setTitle(String t) {
        glfwSetWindowTitle(this.window, t);
    }

    // Register interest in framebuffer resizes. Window never learns who's listening - the
    // composition root decides (e.g. the Camera, so its aspect ratio tracks the window).
    public void addResizeListener(ResizeListener listener) {
        this.resizeListeners.add(listener);
    }

    public double getTime() {
        return glfwGetTime();
    }

    public void shutdown() {
        glfwFreeCallbacks(this.window);
        glfwDestroyWindow(this.window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void initGlfw() {
        glfwSetErrorCallback((error, description) ->
                LOGGER.error("GLFW [{}]: {}", error, GLFWErrorCallback.getDescription(description)));
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.window = glfwCreateWindow(this.config.width(), this.config.height(), this.config.title(), NULL, NULL);
        if (this.window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        // Center the window on the primary monitor.
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(this.window, pWidth, pHeight);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode == null) {
                throw new RuntimeException("Failed to get video mode.");
            }
            glfwSetWindowPos(
                    this.window, (vidMode.width() - pWidth.get(0)) / 2, (vidMode.height() - pHeight.get(0)) / 2);
        }

        glfwShowWindow(this.window);
        glfwRequestWindowAttention(this.window);

        glfwSetInputMode(this.window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        System.out.printf("Minecraft started %s!%n", Version.getVersion());
    }

    private void initOpenGL() {
        // Bind the GL context to this thread - must happen before any GL call.
        glfwMakeContextCurrent(this.window);
        glfwSwapInterval(this.config.vsync() ? 1 : 0);
        GL.createCapabilities();
        GLUtil.setupDebugMessageCallback();
        glClearColor(
                this.config.clearColor().red(),
                this.config.clearColor().green(),
                this.config.clearColor().blue(),
                this.config.clearColor().alpha());
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void initCallbacks() {
        glfwSetKeyCallback(this.window, RawInputEvent.KeyEvent.callback(this.queue));
        glfwSetMouseButtonCallback(this.window, RawInputEvent.MouseButtonEvent.callback(this.queue));
        glfwSetCursorPosCallback(this.window, RawInputEvent.MouseMoveEvent.callback(this.queue));
        glfwSetScrollCallback(this.window, RawInputEvent.ScrollEvent.callback(this.queue));
        glfwSetFramebufferSizeCallback(this.window, resizeCallback());
    }

    // GLFW fires this during glfwPollEvents() on the main thread, so notifying listeners straight
    // from here needs no cross-thread handoff.
    private GLFWFramebufferSizeCallbackI resizeCallback() {
        return (long window, int width, int height) -> {
            glViewport(0, 0, width, height);
            for (ResizeListener listener : this.resizeListeners) listener.onResize(width, height);
        };
    }
}
