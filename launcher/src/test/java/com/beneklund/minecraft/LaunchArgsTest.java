package com.beneklund.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beneklund.minecraft.container.ClientConfig;
import com.beneklund.minecraft.container.ServerConfig;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.junit.jupiter.api.Test;

public class LaunchArgsTest {
    @Test
    void joinParsesHostAndPort() throws ArgumentParserException {
        assertEquals(
                new LaunchMode.Join("10.0.0.39", 25565, ClientConfig.DEFAULT),
                LaunchArgs.parse(new String[] {"--join", "10.0.0.39:25565"}));
    }

    @Test
    void noArgsHostsOnTheDefaultPort() throws ArgumentParserException {
        assertEquals(
                new LaunchMode.Host(LaunchArgs.DEFAULT_PORT, ClientConfig.DEFAULT, ServerConfig.DEFAULT),
                LaunchArgs.parse(new String[] {}));
    }

    @Test
    void joinWithoutAPortUsesTheDefault() throws ArgumentParserException {
        assertEquals(
                new LaunchMode.Join("10.0.0.39", LaunchArgs.DEFAULT_PORT, ClientConfig.DEFAULT),
                LaunchArgs.parse(new String[] {"-j", "10.0.0.39"}));
    }

    // ":".split(":") is an empty array, so this one used to index out of bounds.
    @Test
    void aBareColonIsRefused() {
        refused("--join", ":");
    }

    @Test
    void anEmptyHostIsRefused() {
        refused("--join", ":25565");
    }

    // 0 means "any port" when binding; nothing can connect to it.
    @Test
    void aPortOutOfRangeIsRefused() {
        refused("--join", "host:0");
        refused("--join", "host:70000");
    }

    @Test
    void aPortThatIsNotANumberIsRefused() {
        refused("--join", "host:abc");
    }

    @Test
    void tooManyColonsIsRefused() {
        refused("--join", "host:1:2");
    }

    @Test
    void joinWithNoAddressIsRefused() {
        refused("--join");
    }

    @Test
    void anUnknownFlagIsRefused() {
        refused("--dedicated");
    }

    private static void refused(String... args) {
        assertThrows(ArgumentParserException.class, () -> LaunchArgs.parse(args));
    }
}
