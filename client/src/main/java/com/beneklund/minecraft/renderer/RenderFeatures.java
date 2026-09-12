package com.beneklund.minecraft.renderer;

/*
 * Which of the optional passes a frame is allowed to run. Built once in GameContainer from
 * local.properties and handed to Renderer and PostProcessor, which are the only two places that
 * sequence passes.
 *
 * Every flag here is something that can be switched off without a shader edit — either the pass is
 * skipped outright, or a uniform is uploaded with the value that makes its branch not matter
 * (uExtinction = 0 for haze, uCascadeSplit = 0 for shadows). That constraint is why the record has
 * these five members and not, say, ambient occlusion: AO is baked into the vertex data by the
 * mesher, and turning it off would mean remeshing the world.
 *
 * Not a per-effect set of properties keys. One preset means one "just the world" switch and no
 * combination of half-on states to reason about; if a single effect ever needs isolating, that is
 * a temporary edit to simple() rather than a config surface to maintain.
 */
public record RenderFeatures(boolean sunShadows, boolean clouds, boolean godrays, boolean bloom, boolean distanceHaze) {

    // What the game does with no local.properties, which is the normal case.
    public static RenderFeatures full() {
        return new RenderFeatures(true, true, true, true, true);
    }

    /*
     * Terrain, sky, and nothing else. Kept: baked AO, the per-face brightness bands, the mesher's
     * sky and torch light, Lambert N.L, the Preetham sky with its night gradient, and the ACES
     * tonemap — the scene is authored in linear radiance (a fully lit face is 15.4), so dropping
     * the tonemap would blow the whole image to white rather than simplify it.
     *
     * The sun needs no flag of its own. With bloom off, the disc is SUN_INTENSITY = 270 through
     * the tonemap at exposure 0.115, which is a flat white circle with no halo around it.
     */
    public static RenderFeatures simple() {
        return new RenderFeatures(false, false, false, false, false);
    }
}
