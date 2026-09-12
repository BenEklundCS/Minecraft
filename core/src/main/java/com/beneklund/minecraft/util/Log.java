package com.beneklund.minecraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Single shared logger for the whole game. Import Log.LOGGER statically to keep call sites terse.
//
// The category loggers below are children of "minecraft", so logback's level inheritance is the
// whole filtering story — there's no custom gating layer here. Set "minecraft" to DEBUG and every
// category talks; set one child and only that child does. Levels live in logback.xml, which reads
// a -Dlog.<category> property per category, so you can flip one from the run configuration without
// editing anything.
//
// Pick the category that matches the subsystem, not the class. LOGGER stays the right choice for
// startup/shutdown and anything that doesn't belong to one subsystem.
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("minecraft");

    // Chunk streaming: load/unload decisions, the generate -> mesh -> upload pipeline, job failures.
    public static final Logger CHUNK = category("chunk");

    // World state: generation, block edits, neighbour invalidation.
    public static final Logger WORLD = category("world");

    // Draw submission, shader compile/reload, atlas stitching.
    public static final Logger RENDER = category("render");

    // GL/GLFW context lifecycle and raw GL object create/delete. Named GPU rather than GL because
    // the files that use it also import org.lwjgl.opengl.GL, and a single-type import would win.
    public static final Logger GPU = category("gpu");

    // Raw events through to mapped actions.
    public static final Logger INPUT = category("input");

    // Movement, physics, interaction.
    public static final Logger PLAYER = category("player");

    // Disk and classpath: saves, resource packs, texture and audio decoding.
    public static final Logger IO = category("io");

    // OpenAL device lifecycle and playback.
    public static final Logger AUDIO = category("audio");

    // Frame timing and queue depths. Noisy by nature, so it gets its own category — you can turn
    // the per-second summary on without turning on everything else.
    public static final Logger PERF = category("perf");

    private static Logger category(String name) {
        return LoggerFactory.getLogger("minecraft." + name);
    }

    private Log() {}
}
