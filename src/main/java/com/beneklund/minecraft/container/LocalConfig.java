package com.beneklund.minecraft.container;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

public class LocalConfig {
    private final Properties props = new Properties();

    public LocalConfig() {
        try (var in = new FileInputStream("local.properties")) {
            this.props.load(in);
        } catch (IOException ignored) {
            // no local.properties — all settings will be absent
        }
    }

    public Optional<String> startupDisc() {
        return Optional.ofNullable(this.props.getProperty("startup.disc"));
    }
}
