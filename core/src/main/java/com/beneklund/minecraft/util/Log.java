package com.beneklund.minecraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The game's SLF4J loggers: one root and a child per subsystem. Import them statically.
 *
 * <p>Each category is a child of {@code minecraft}, so logback's level inheritance does all the
 * filtering. {@code minecraft} at DEBUG turns every category on; a level on one child affects only
 * that child. {@code logback.xml} reads a {@code -Dlog.<category>} property per category, so a
 * level changes from the run configuration without an edit.
 *
 * <p>Pick the category by subsystem, not by class. {@link #LOGGER} is for startup, shutdown and
 * anything that spans subsystems.
 *
 * @see <a href="https://logback.qos.ch/manual/architecture.html#effectiveLevel">Logback: Effective
 *     level inheritance</a>
 */
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("minecraft");

    /** Chunk streaming: load and unload decisions, the generate, mesh, upload pipeline, job failures. */
    public static final Logger CHUNK = category("chunk");

    /** World state: generation, block edits, neighbour invalidation. */
    public static final Logger WORLD = category("world");

    /** Draw submission, shader compile and reload, atlas stitching. */
    public static final Logger RENDER = category("render");

    /**
     * GL and GLFW context lifecycle and GL object creation and deletion. Named {@code GPU} because
     * the files that use it also import {@code org.lwjgl.opengl.GL}, which a logger named {@code GL}
     * would collide with.
     */
    public static final Logger GPU = category("gpu");

    /** Raw events through to mapped actions. */
    public static final Logger INPUT = category("input");

    /** Movement, physics, interaction. */
    public static final Logger PLAYER = category("player");

    /** Disk and classpath: saves, resource packs, texture and audio decoding. */
    public static final Logger IO = category("io");

    /** OpenAL device lifecycle and playback. */
    public static final Logger AUDIO = category("audio");

    /**
     * Frame timing and queue depths. A category of its own because it is noisy, so the per-second
     * summary turns on without everything else.
     */
    public static final Logger PERF = category("perf");

    private static Logger category(String name) {
        return LoggerFactory.getLogger("minecraft." + name);
    }

    private Log() {}
}
