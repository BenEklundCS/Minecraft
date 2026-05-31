package com.beneklund.minecraft.container;

import org.joml.Vector3f;

// Starting state for the camera. Passed to GameContainer so spawn position,
// initial pitch, and FOV live in data rather than inline constructor arguments.
public record CameraConfig(Vector3f startPosition, float startPitch, float fov) {}
