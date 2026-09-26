package com.beneklund.minecraft.player;

import org.joml.Vector3f;

/** Something the player did this tick that the game loop reacts to beyond the world edit itself. */
public sealed interface Interaction {
    /**
     * A block break or place, with the ray that selected it, for the debug overlay.
     *
     * @param broken true for a break, false for a place
     */
    record BlockInteraction(boolean broken, Vector3f eye, Vector3f dir, RaycastResult result) implements Interaction {}
}
