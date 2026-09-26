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

/**
 * The OS window, its OpenGL context, and the frame boundary ({@link #beginFrame()}, {@link
 * #endFrame()}). The only class that calls GLFW.
 *
 * <p>{@link #init()} runs in three steps, and nothing may touch GL before it returns:
 *
 * <ol>
 *   <li>Creates the window with hints for an OpenGL 3.3 core profile context. Core drops the
 *       fixed-function and other deprecated API, so every draw goes through shaders, VAOs and
 *       buffers. The window is created hidden, sized for the {@link WindowConfig.Mode},
 *       centred on the primary monitor, then shown. The cursor is disabled, which hides it and
 *       reports unbounded virtual positions for mouse look.
 *   <li>Makes the context current on the calling thread, which becomes the only thread allowed
 *       to call GL. {@code GL.createCapabilities()} then loads the function pointers the driver
 *       supports; LWJGL can't call any GL function before it. With {@code debugEnabled}, LWJGL's
 *       debug message callback prints driver messages as they happen. Depth testing ({@code
 *       GL_LEQUAL}) and back-face culling are enabled once here.
 *   <li>Routes key, mouse button, cursor and scroll callbacks into the {@link InputEventQueue},
 *       and framebuffer resizes to the {@link IResizeListener}s.
 * </ol>
 *
 * <p>The context is double-buffered: the frame draws into the back buffer, and {@link #endFrame()}
 * swaps it to the screen whole. Swap interval 1 when {@code vsync} is set waits for vertical
 * blank; 0 swaps immediately.
 *
 * @see <a href="https://www.glfw.org/docs/latest/window_guide.html">GLFW: Window guide</a>
 * @see <a href="https://www.glfw.org/docs/latest/context_guide.html">GLFW: Context guide</a>
 * @see <a href="https://www.glfw.org/docs/latest/input_guide.html#cursor_mode">GLFW: Cursor
 *     mode</a>
 * @see <a href="https://www.lwjgl.org/guide">LWJGL: Getting started</a>
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

    /**
     * Processes pending OS events. Every input and resize callback runs inside this call, on the
     * calling thread.
     *
     * @see <a href="https://www.glfw.org/docs/latest/input_guide.html#events">GLFW: Event
     *     processing</a>
     */
    public void pollEvents() {
        glfwPollEvents();
    }

    /** Clears colour and depth of the bound framebuffer. */
    public void beginFrame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /** Swaps the back buffer to the screen, blocking for vertical blank when vsync is on. */
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

    /**
     * Adds a framebuffer resize listener. The composition root decides who listens, e.g. the
     * camera so its aspect ratio tracks the window.
     */
    public void addResizeListener(IResizeListener listener) {
        resizeListeners.add(listener);
    }

    /** Seconds since GLFW initialised, from a monotonic high-resolution timer. */
    public double getTime() {
        return glfwGetTime();
    }

    public void setClearColor(Color clearColor) {
        glClearColor(clearColor.red(), clearColor.green(), clearColor.blue(), clearColor.alpha());
    }

    /** Frees the callbacks, destroys the window and context, and terminates GLFW. */
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

    /**
     * Resets the viewport and notifies listeners. GLFW calls this during {@link #pollEvents()} on
     * the main thread, so listeners may touch GL directly.
     */
    private GLFWFramebufferSizeCallbackI resizeCallback() {
        return (long window, int width, int height) -> {
            GPU.debug("framebuffer resized to {}x{}, notifying {} listener(s)", width, height, resizeListeners.size());
            glViewport(0, 0, width, height);
            for (IResizeListener listener : resizeListeners) listener.onResize(width, height);
        };
    }

    private void setWindowHints() {}
}
