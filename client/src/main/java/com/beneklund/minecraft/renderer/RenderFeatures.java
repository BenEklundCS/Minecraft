package com.beneklund.minecraft.renderer;

/**
 * The optional passes a frame runs. Built once in {@code GameContainer} from {@code
 * local.properties} and handed to {@link Renderer} and {@link PostProcessor}, the two places that
 * sequence passes.
 *
 * <p>Each flag switches off without a shader edit: the pass is skipped, or a uniform is uploaded
 * with the value that neutralises its branch ({@code uExtinction = 0} for haze, {@code
 * uCascadeSplit = 0} for shadows). Ambient occlusion has no flag because the mesher bakes it into
 * vertex data, and turning it off would mean remeshing the world.
 *
 * <p>There are two presets and no per-effect keys, so there is one "just the world" switch and no
 * half-on combinations to reason about. Isolating one effect is a temporary edit to {@link
 * #simple()}.
 */
public record RenderFeatures(boolean sunShadows, boolean clouds, boolean godrays, boolean bloom, boolean distanceHaze) {

    /** Every pass on. What the game runs with no {@code local.properties}. */
    public static RenderFeatures full() {
        return new RenderFeatures(true, true, true, true, true);
    }

    /**
     * Terrain and sky only.
     *
     * <p>Kept: baked AO, the per-face brightness bands, the mesher's sky and torch light, Lambert
     * N.L, the Preetham sky with its night gradient, and the ACES tonemap. The scene is authored in
     * linear radiance (a fully lit face is 15.4), so the tonemap stays or the image blows out to
     * white.
     *
     * <p>The sun disc needs no flag. With bloom off it is {@code SUN_INTENSITY = 270} through the
     * tonemap at exposure 0.115: a flat white circle with no halo.
     */
    public static RenderFeatures simple() {
        return new RenderFeatures(false, false, false, false, false);
    }
}
