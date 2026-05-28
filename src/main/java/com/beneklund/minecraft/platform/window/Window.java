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
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

public class Window {
    // The window handle
    private long window;
    private InputEventQueue inputQueue;
    private WindowConfig config;

    public Window(WindowConfig config, InputEventQueue inputQueue) {
        this.config = config;
        this.inputQueue = inputQueue;
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

    public double getTime() {
        return glfwGetTime();
    }

    public void shutdown() {
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(this.window);
        glfwDestroyWindow(this.window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void initGlfw() {
        glfwSetErrorCallback(
                (error, description) ->
                        LOGGER.error(
                                "GLFW [{}]: {}",
                                error,
                                GLFWErrorCallback.getDescription(description)));
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.window =
                glfwCreateWindow(
                        this.config.width(), this.config.height(), this.config.title(), NULL, NULL);
        if (this.window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(this.window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    this.window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwShowWindow(this.window);
        System.out.println("Minecraft started " + Version.getVersion() + "!");
    }

    private void initOpenGL() {
        glfwMakeContextCurrent(this.window);
        glfwSwapInterval(this.config.vsync() ? 1 : 0);
        GL.createCapabilities();
        glClearColor(
                this.config.clearColor().red(),
                this.config.clearColor().green(),
                this.config.clearColor().blue(),
                this.config.clearColor().alpha());
    }

    private void initCallbacks() {
        glfwSetKeyCallback(
                this.window,
                (w, key, scancode, action, mods) ->
                        this.inputQueue.offer(
                                new RawInputEvent.KeyEvent(key, scancode, action, mods)));

        glfwSetMouseButtonCallback(
                this.window,
                (w, button, action, mods) ->
                        this.inputQueue.offer(new RawInputEvent.MouseButtonEvent(button, action, mods)));

        glfwSetCursorPosCallback(
                this.window,
                (w, x, y) -> this.inputQueue.offer(new RawInputEvent.MouseMoveEvent(x, y)));

        glfwSetScrollCallback(
                this.window,
                (w, xOffset, yOffset) ->
                        this.inputQueue.offer(new RawInputEvent.ScrollEvent(xOffset, yOffset)));
    }
}
