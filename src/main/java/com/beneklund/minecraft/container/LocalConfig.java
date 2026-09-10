package com.beneklund.minecraft.container;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

// Optional developer overrides loaded from local.properties in the working directory.
// The file is intentionally not on the classpath — it's a per-machine dev tool, not a
// shipped config. Missing file is normal; all getters just return Optional.empty().
public class LocalConfig {
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final int DEFAULT_DEBUG_SERVER_PORT = 8099;
    private final Properties props = new Properties();

    public LocalConfig() {
        try (var in = new FileInputStream("local.properties")) {
            props.load(in);
        } catch (IOException ignored) {
            // no local.properties — all settings will be absent
        }
    }

    // e.g. "music/public/Kai_Engel_-_01_-_Prologue.ogg" — plays on startup if set.
    public Optional<String> startupDisc() {
        return Optional.ofNullable(props.getProperty("startup.disc"));
    }

    public Optional<String> preferredAlbum() {
        return Optional.ofNullable(props.getProperty("preferred.album"));
    }

    /*
     * debugserver.enabled=true turns on the debug HTTP server and the frame stream it serves.
     *
     * Its own flag rather than "the port is set", because the two answer different questions and
     * the server is not free: it does a glReadPixels every 100 ms whether or not a browser is
     * connected, and glReadPixels drains the whole GL pipeline. Measured on an RTX 2070 at render
     * distance 32, that is worth ~10 ms of p99 — invisible standing still, obvious while flying.
     *
     * Keeping the port in a separate key means you can switch the server off for a timing run and
     * back on without losing the port you always use.
     */
    public boolean debugServerEnabled() {
        return "true".equals(props.getProperty("debugserver.enabled"));
    }

    // Port for the debug server and its live frame stream. Only read when debugserver.enabled is
    // true; a malformed or absent value falls back to the default rather than silently not
    // starting, because "enabled" already said what was wanted.
    public int debugServerPort() {
        try {
            return Optional.ofNullable(props.getProperty("framestream.port"))
                    .map(Integer::parseInt)
                    .orElse(DEFAULT_DEBUG_SERVER_PORT);
        } catch (NumberFormatException e) {
            return DEFAULT_DEBUG_SERVER_PORT;
        }
    }

    // gputimer.enabled=true turns on the per-pass GPU timers. Opt-in because a query per
    // pass per frame is cheap but not free, and a before/after comparison wants it off.
    public boolean gpuTimerEnabled() {
        return "true".equals(props.getProperty("gputimer.enabled"));
    }

    // shaders.simple=true strips the frame back to terrain and sky — no cast shadows, no clouds,
    // no light shafts, no bloom, no distance haze. Opt-in and absent-means-off like the rest of
    // this file, so the full pipeline is what anyone without a local.properties sees.
    // GameContainer turns this into a RenderFeatures preset; the list of what that covers lives
    // there, not here.
    public boolean simpleShaders() {
        return "true".equals(props.getProperty("shaders.simple"));
    }

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
