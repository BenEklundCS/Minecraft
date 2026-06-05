package com.beneklund.minecraft.container;

// Camera tuning. Just field-of-view for now — view/projection params live in data
// rather than inline constructor arguments.
public record CameraConfig(float fov) {}
