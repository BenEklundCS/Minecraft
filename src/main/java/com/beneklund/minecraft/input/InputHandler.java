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

    public void handle(List<IInputAction> actions) {
        for (var action : actions) {
            switch (action) {
                case IInputAction.Simple.JUMP -> LOGGER.debug("JUMP");
                case IInputAction.Simple.BREAK_BLOCK -> LOGGER.debug("BREAK_BLOCK");
                case IInputAction.Simple.PLACE_BLOCK -> LOGGER.debug("PLACE_BLOCK");
                case IInputAction.Simple.SLOT_NEXT -> LOGGER.debug("SLOT_NEXT");
                case IInputAction.Simple.SLOT_PREV -> LOGGER.debug("SLOT_PREV");
                case IInputAction.Simple.PAUSE -> LOGGER.debug("PAUSE");
                case IInputAction.Simple.DEBUG_OVERLAY -> LOGGER.debug("DEBUG_OVERLAY");
                case IInputAction.Simple.INVENTORY -> LOGGER.debug("INVENTORY");
                case IInputAction.HotbarActionI.Select h -> LOGGER.debug("SLOT_{}", h.slot() + 1);
                case IInputAction.Simple.EXIT -> this.window.close();
                case IInputAction.MoveActionI m -> LOGGER.debug("MOVE dx={} dz={}", m.dx(), m.dz());
                case IInputAction.LookActionI l -> LOGGER.debug("LOOK dx={} dy={}", l.dx(), l.dy());
                case IInputAction.ScrollActionI s -> {
                    LOGGER.debug("SCROLL delta={}", s.delta());
                    handleScroll(s);
                }
                default -> {}
            }
        }
    }

    private void handleScroll(IInputAction.ScrollActionI scrollAction) {
        // Scroll zooms by adjusting FOV. 45° is the normal Minecraft FOV; clamped so
        // it never goes fisheye or collapses to a point.
        float fov = this.camera.getFov() - scrollAction.delta();
        if (fov < 1.0f) fov = 1.0f;
        if (fov > 45.0f) fov = 45.0f;
        this.camera.setFov(fov);
    }
}
