package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/*
 * The presets only. What each flag actually does to the frame is a GL question and is checked by
 * running the game, not here — but "simple() forgot to turn something off" is a plain data bug and
 * this is where it gets caught.
 */
class RenderFeaturesTest {

    @Test
    void fullEnablesEveryPass() {
        RenderFeatures full = RenderFeatures.full();
        assertTrue(full.sunShadows());
        assertTrue(full.clouds());
        assertTrue(full.godrays());
        assertTrue(full.bloom());
        assertTrue(full.distanceHaze());
    }

    @Test
    void simpleDisablesEveryOptionalPass() {
        RenderFeatures simple = RenderFeatures.simple();
        assertFalse(simple.sunShadows());
        assertFalse(simple.clouds());
        assertFalse(simple.godrays());
        assertFalse(simple.bloom());
        assertFalse(simple.distanceHaze());
    }

    // full() is what the game runs with no local.properties at all, so a flag that defaulted the
    // wrong way would silently ship the stripped-down look to anyone who never set the key.
    @Test
    void presetsDisagreeOnEveryFlag() {
        RenderFeatures full = RenderFeatures.full();
        RenderFeatures simple = RenderFeatures.simple();
        assertFalse(full.sunShadows() == simple.sunShadows());
        assertFalse(full.clouds() == simple.clouds());
        assertFalse(full.godrays() == simple.godrays());
        assertFalse(full.bloom() == simple.bloom());
        assertFalse(full.distanceHaze() == simple.distanceHaze());
    }
}
