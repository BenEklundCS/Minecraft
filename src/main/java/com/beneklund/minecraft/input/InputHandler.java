package com.beneklund.minecraft.input;

import static com.beneklund.minecraft.util.Log.LOGGER;

import com.beneklund.minecraft.platform.input.InputAction;
import com.beneklund.minecraft.platform.window.Window;
import java.util.List;

public class InputHandler {
    private final Window window;

    public InputHandler(Window window) {
        this.window = window;
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
                case InputAction.Simple.SLOT_1 -> LOGGER.debug("SLOT_1");
                case InputAction.Simple.SLOT_2 -> LOGGER.debug("SLOT_2");
                case InputAction.Simple.SLOT_3 -> LOGGER.debug("SLOT_3");
                case InputAction.Simple.SLOT_4 -> LOGGER.debug("SLOT_4");
                case InputAction.Simple.SLOT_5 -> LOGGER.debug("SLOT_5");
                case InputAction.Simple.SLOT_6 -> LOGGER.debug("SLOT_6");
                case InputAction.Simple.SLOT_7 -> LOGGER.debug("SLOT_7");
                case InputAction.Simple.SLOT_8 -> LOGGER.debug("SLOT_8");
                case InputAction.Simple.SLOT_9 -> LOGGER.debug("SLOT_9");
                case InputAction.Simple.EXIT -> this.window.close();
                case InputAction.MoveAction m -> LOGGER.debug("MOVE dx={} dz={}", m.dx(), m.dz());
                case InputAction.LookAction l -> LOGGER.debug("LOOK dx={} dy={}", l.dx(), l.dy());
                case InputAction.ScrollAction s -> LOGGER.debug("SCROLL delta={}", s.delta());
                default -> {}
            }
        }
    }
}
