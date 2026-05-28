package com.beneklund.minecraft.platform.input;

public sealed interface RawInputEvent {
    record KeyEvent(int key, int scancode, int action, int mods) implements RawInputEvent {}

    record MouseMoveEvent(double xpos, double ypos) implements RawInputEvent {}

    record ScrollEvent(double xoffset, double yoffset) implements RawInputEvent {}

    record MouseButtonEvent(int button, int action, int mods) implements RawInputEvent {}
}
