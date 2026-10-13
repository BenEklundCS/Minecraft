package com.beneklund.minecraft;

import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

/**
 * Entry point: hosts a local server by default, or joins one with {@code --join HOST[:PORT]}. Uses Java 21's preview instance
 * {@code main} (JEP 445), hence {@code --enable-preview}.
 *
 * @see <a href="https://openjdk.org/jeps/445">JEP 445: Unnamed Classes and Instance Main
 *     Methods</a>
 */
class Main {
    void main(String[] args) throws Exception {
        LaunchMode mode;
        try {
            mode = LaunchArgs.parse(args);
        } catch (HelpScreenException e) {
            // --help already printed itself; asking for it isn't an error.
            return;
        } catch (ArgumentParserException e) {
            e.getParser().handleError(e);
            System.exit(1);
            return;
        }
        Launcher.launch(mode);
    }
}
