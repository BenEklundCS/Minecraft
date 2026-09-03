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

    // Port for the live frame stream, e.g. framestream.port=8099. Absent means off — this opens
    // a socket, so it stays opt-in rather than defaulting on.
    public Optional<Integer> frameStreamPort() {
        try {
            return Optional.ofNullable(props.getProperty("framestream.port")).map(Integer::parseInt);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // gputimer.enabled=true turns on the per-pass GPU timers. Opt-in because a query per
    // pass per frame is cheap but not free, and a before/after comparison wants it off.
    public boolean gpuTimerEnabled() {
        return "true".equals(props.getProperty("gputimer.enabled"));
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
