package com.beneklund.minecraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Single shared logger for the whole game. Import Log.LOGGER statically to keep call sites terse.
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("minecraft");

    private Log() {}
}
