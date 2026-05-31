package com.beneklund.minecraft.input;

import static com.beneklund.minecraft.util.Log.LOGGER;

import com.beneklund.minecraft.platform.window.Window;
import com.beneklund.minecraft.renderer.Camera;
import java.util.List;

// Placeholder game-logic handler. Most Simple actions are stubs right now - they just log.
// Move and look are handled in Game (frame-rate-sensitive); scroll lives here because
// FOV adjustment is a camera concern, not a game-loop concern.
public class InputHandler {

    private final Window window;
    private final Camera camera;

    public InputHandler(Window window, Camera camera) {
        this.window = window;
        this.camera = camera;
    }

    public void handle(List<InputAction> actions) {
        for (var action : actions) {
            switch (action) {
                case InputAction.Simple.JUMP -> LOGGER.debug("JUMP");
                case InputAction.Simple.BREAK_BLOCK -> LOGGER.debug("BREAK_BLOCK");
                case InputAction.Simple.PLACE_BLOCK -> LOGGER.debug("PLACE_BLOCK");
                case InputAction.Simple.SLOT_NEXT -> LOGGER.debug("SLOT_NEXT");
                case InputAction.Simple.SLOT_PREV -> LOGGER.debug("SLOT_PREV");
                case InputAction.Simple.PAUSE -> LOGGER.debug("PAUSE");
                case InputAction.Simple.DEBUG_OVERLAY -> LOGGER.debug("DEBUG_OVERLAY");
                case InputAction.Simple.INVENTORY -> LOGGER.debug("INVENTORY");
                case InputAction.HotbarAction.Select h -> LOGGER.debug("SLOT_{}", h.slot() + 1);
                case InputAction.Simple.EXIT -> this.window.close();
                case InputAction.MoveAction m -> LOGGER.debug("MOVE dx={} dz={}", m.dx(), m.dz());
                case InputAction.LookAction l -> LOGGER.debug("LOOK dx={} dy={}", l.dx(), l.dy());
                case InputAction.ScrollAction s -> {
                    LOGGER.debug("SCROLL delta={}", s.delta());
                    handleScroll(s);
                }
                default -> {}
            }
        }
    }

    private void handleScroll(InputAction.ScrollAction scrollAction) {
        // Scroll zooms by adjusting FOV. 45° is the normal Minecraft FOV; clamped so
        // it never goes fisheye or collapses to a point.
        float fov = this.camera.getFov() - scrollAction.delta();
        if (fov < 1.0f) fov = 1.0f;
        if (fov > 45.0f) fov = 45.0f;
        this.camera.setFov(fov);
    }
}
