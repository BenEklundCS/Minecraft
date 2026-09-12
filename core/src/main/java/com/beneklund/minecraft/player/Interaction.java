package com.beneklund.minecraft.player;

import com.beneklund.minecraft.util.RaycastResult;
import org.joml.Vector3f;

public sealed interface Interaction {
    record BlockInteraction(boolean broken, Vector3f eye, Vector3f dir, RaycastResult result) implements Interaction {}
}
