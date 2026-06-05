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
            this.props.load(in);
        } catch (IOException ignored) {
            // no local.properties — all settings will be absent
        }
    }

    // e.g. "music/c418/disc_cat.ogg" — plays on startup if set.
    public Optional<String> startupDisc() {
        return Optional.ofNullable(this.props.getProperty("startup.disc"));
    }

    public boolean debugEnabled() {
        Optional<String> prop = Optional.ofNullable(this.props.getProperty("debug.enabled"));
        if (prop.isPresent()) {
            String propValue = prop.get();
            if (propValue.equals("true") || propValue.equals("false")) {
                return propValue.equals("true");
            }
        }
        return DEFAULT_DEBUG_MODE;
    }
}
