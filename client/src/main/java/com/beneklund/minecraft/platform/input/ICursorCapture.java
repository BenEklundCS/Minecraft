package com.beneklund.minecraft.platform.input;

/**
 * Where {@link InputMapper} sends the cursor mode it has decided on: captured hides the cursor and
 * locks it to the window for mouse look, released hands it back to the desktop.
 */
@FunctionalInterface
public interface ICursorCapture {
    void setCursorCaptured(boolean captured);
}
