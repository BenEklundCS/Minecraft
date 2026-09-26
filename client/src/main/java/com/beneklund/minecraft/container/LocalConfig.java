package com.beneklund.minecraft.container;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

/**
 * Per-machine developer overrides, read once from {@code local.properties} in the working
 * directory.
 *
 * <p>The file lives outside the classpath and is gitignored because it is a dev tool for one
 * machine. A missing file is the normal case: every flag reads as off, every optional as empty,
 * and anyone without the file gets the full default pipeline.
 */
public class LocalConfig {
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final int DEFAULT_DEBUG_SERVER_PORT = 8099;
    private final Properties props = new Properties();

    public LocalConfig() {
        try (var in = new FileInputStream("local.properties")) {
            props.load(in);
        } catch (IOException ignored) {
        }
    }

    /**
     * {@code startup.disc}: a track to play on startup, e.g. {@code
     * music/public/Kai_Engel_-_01_-_Prologue.ogg}.
     */
    public Optional<String> startupDisc() {
        return Optional.ofNullable(props.getProperty("startup.disc"));
    }

    public Optional<String> preferredAlbum() {
        return Optional.ofNullable(props.getProperty("preferred.album"));
    }

    /**
     * {@code debugserver.enabled=true} starts the debug HTTP server and its frame stream.
     *
     * <p>The server costs frame time: it runs a synchronous {@code glReadPixels} every 100 ms
     * whether or not a browser is connected, which drains the GL pipeline. Measured on an RTX 2070
     * at render distance 32, that is about 10 ms of p99 frame time, unnoticeable standing still
     * and obvious while flying. The enable flag is separate from the port so a timing run can turn
     * the server off without losing the configured port.
     */
    public boolean debugServerEnabled() {
        return "true".equals(props.getProperty("debugserver.enabled"));
    }

    /**
     * {@code framestream.port}: the debug server's port, read only when {@link
     * #debugServerEnabled()} is true. An absent or malformed value falls back to 8099, because the
     * enable flag already asked for a running server.
     */
    public int debugServerPort() {
        try {
            return Optional.ofNullable(props.getProperty("framestream.port"))
                    .map(Integer::parseInt)
                    .orElse(DEFAULT_DEBUG_SERVER_PORT);
        } catch (NumberFormatException e) {
            return DEFAULT_DEBUG_SERVER_PORT;
        }
    }

    /**
     * {@code gputimer.enabled=true} turns on the per-pass GPU timers. Opt-in because a query per
     * pass per frame has a small cost, and a before/after comparison wants it off.
     */
    public boolean gpuTimerEnabled() {
        return "true".equals(props.getProperty("gputimer.enabled"));
    }

    /**
     * {@code shaders.simple=true} strips the frame back to terrain and sky. {@code GameContainer}
     * turns it into a {@code RenderFeatures} preset, and that class lists what each flag switches
     * off.
     */
    public boolean simpleShaders() {
        return "true".equals(props.getProperty("shaders.simple"));
    }

    /** {@code debug.enabled}: exactly {@code true} or {@code false}; anything else reads as off. */
    public boolean debugEnabled() {
        Optional<String> prop = Optional.ofNullable(props.getProperty("debug.enabled"));
        if (prop.isPresent()) {
            String propValue = prop.get();
            if (propValue.equals("true") || propValue.equals("false")) {
                return propValue.equals("true");
            }
        }
        return DEFAULT_DEBUG_MODE;
    }
}
