package com.beneklund.minecraft.input;

import static com.beneklund.minecraft.util.Log.INPUT;

import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.renderer.Camera;
import java.util.List;

// Placeholder game-logic handler — everything but EXIT is a stub that just logs. Move, look and
// scroll are acted on in Game and Player.
public class InputHandler {

    private final Window window;
    private final Camera camera;

    public InputHandler(Window window, Camera camera) {
        this.window = window;
        this.camera = camera;
    }

    public void handle(List<IInputAction> actions) {
        for (var action : actions) {
            switch (action) {
                case IInputAction.Simple.JUMP -> INPUT.debug("JUMP");
                case IInputAction.Simple.BREAK_BLOCK -> INPUT.debug("BREAK_BLOCK");
                case IInputAction.Simple.PLACE_BLOCK -> INPUT.debug("PLACE_BLOCK");
                case IInputAction.Simple.SLOT_NEXT -> INPUT.debug("SLOT_NEXT");
                case IInputAction.Simple.SLOT_PREV -> INPUT.debug("SLOT_PREV");
                case IInputAction.Simple.PAUSE -> INPUT.debug("PAUSE");
                case IInputAction.Simple.DEBUG_OVERLAY -> INPUT.debug("DEBUG_OVERLAY");
                case IInputAction.Simple.INVENTORY -> INPUT.debug("INVENTORY");
                case IInputAction.HotbarAction.Select h -> INPUT.debug("SLOT_{}", h.slot() + 1);
                case IInputAction.Simple.EXIT -> window.close();
                case IInputAction.MoveAction m -> INPUT.debug("MOVE dx={} dz={}", m.dx(), m.dz());
                case IInputAction.LookAction l -> INPUT.debug("LOOK dx={} dy={}", l.dx(), l.dy());
                case IInputAction.ScrollAction s -> {
                    INPUT.debug("SCROLL delta={}", s.delta());
                }
                default -> {}
            }
        }
    }
}
