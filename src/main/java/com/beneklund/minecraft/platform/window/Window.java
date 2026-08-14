package com.beneklund.minecraft.platform.window;

import static com.beneklund.minecraft.util.Log.GPU;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.beneklund.minecraft.container.WindowConfig;
import com.beneklund.minecraft.platform.input.IRawInputEvent;
import com.beneklund.minecraft.platform.input.InputEventQueue;
import com.beneklund.minecraft.util.Color;
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
    private final List<IResizeListener> resizeListeners = new ArrayList<>();
    private int width;
    private int height;

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
        return glfwWindowShouldClose(window);
    }

    public void close() {
        glfwSetWindowShouldClose(window, true);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void beginFrame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void endFrame() {
        glfwSwapBuffers(window);
    }

    public void setTitle(String t) {
        glfwSetWindowTitle(window, t);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // Register interest in framebuffer resizes. Window never learns who's listening - the
    // composition root decides (e.g. the Camera, so its aspect ratio tracks the window).
    public void addResizeListener(IResizeListener listener) {
        resizeListeners.add(listener);
    }

    public double getTime() {
        return glfwGetTime();
    }

    public void setClearColor(Color clearColor) {
        glClearColor(clearColor.red(), clearColor.green(), clearColor.blue(), clearColor.alpha());
    }

    public void shutdown() {
        GPU.info("destroying window and terminating GLFW");
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void initGlfw() {
        glfwSetErrorCallback((error, description) ->
                GPU.error("GLFW [{}]: {}", error, GLFWErrorCallback.getDescription(description)));
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        WindowConfig.Mode mode = config.mode();

        width = config.width();
        height = config.height();
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode videoMode = glfwGetVideoMode(monitor);
        if (videoMode == null) throw new RuntimeException("Failed to get video mode.");

        if (mode.fullscreen()) {
            width = videoMode.width();
            height = videoMode.height();
        } else {
            monitor = NULL;
        }

        window = glfwCreateWindow(width, height, config.title(), monitor, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        if (mode == WindowConfig.Mode.WINDOWED_FULLSCREEN) {
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
            int rr = videoMode.refreshRate();
            glfwSetWindowMonitor(window, monitor, 0, 0, width, height, rr);
        }

        // Center the window on the primary monitor.
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode == null) {
                throw new RuntimeException("Failed to get video mode.");
            }
            glfwSetWindowPos(window, (vidMode.width() - pWidth.get(0)) / 2, (vidMode.height() - pHeight.get(0)) / 2);
        }

        glfwShowWindow(window);
        glfwRequestWindowAttention(window);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        GPU.info("Minecraft started {}!", Version.getVersion());
        GPU.debug(
                "window {}x{} vsync={} debug={}",
                config.width(),
                config.height(),
                config.vsync(),
                config.debugEnabled());
    }

    private void initOpenGL() {
        // Bind the GL context to this thread - must happen before any GL call.
        glfwMakeContextCurrent(window);
        glfwSwapInterval(config.vsync() ? 1 : 0);
        GL.createCapabilities();
        // Which driver you actually got. First thing worth knowing when rendering looks wrong on
        // one machine and fine on another, and the first thing to paste into a bug report.
        GPU.info("GL {} | {} | {}", glGetString(GL_VERSION), glGetString(GL_RENDERER), glGetString(GL_VENDOR));
        if (config.debugEnabled()) {
            GLUtil.setupDebugMessageCallback();
        }
        setClearColor(config.clearColor());
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
    }

    private void initCallbacks() {
        glfwSetKeyCallback(window, IRawInputEvent.KeyEvent.callback(queue));
        glfwSetMouseButtonCallback(window, IRawInputEvent.MouseButtonEvent.callback(queue));
        glfwSetCursorPosCallback(window, IRawInputEvent.MouseMoveEvent.callback(queue));
        glfwSetScrollCallback(window, IRawInputEvent.ScrollEvent.callback(queue));
        glfwSetFramebufferSizeCallback(window, resizeCallback());
    }

    // GLFW fires this during glfwPollEvents() on the main thread, so notifying listeners straight
    // from here needs no cross-thread handoff.
    private GLFWFramebufferSizeCallbackI resizeCallback() {
        return (long window, int width, int height) -> {
            GPU.debug("framebuffer resized to {}x{}, notifying {} listener(s)", width, height, resizeListeners.size());
            glViewport(0, 0, width, height);
            for (IResizeListener listener : resizeListeners) listener.onResize(width, height);
        };
    }

    private void setWindowHints() {}
}
