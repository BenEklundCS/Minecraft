package com.beneklund.minecraft.world;

/**
 * The world's identity and how much of it the server keeps loaded.
 *
 * @param seed world generation seed, also the name of the save directory
 * @param loadRadius radius in chunks kept loaded around the player
 */
public record WorldConfig(long seed, int loadRadius) {}
