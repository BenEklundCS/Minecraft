package com.beneklund.minecraft.world;

import com.beneklund.minecraft.input.InputAction;
import com.beneklund.minecraft.input.InputHandler;
import java.util.List;

public class World {
    private final InputHandler inputHandler;

    public World(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public void update(List<InputAction> actions, float dt) {
        this.inputHandler.handle(actions);
    }
}
