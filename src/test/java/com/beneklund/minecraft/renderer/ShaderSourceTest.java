package com.beneklund.minecraft.renderer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/*
 * Guards a rule that cannot be checked any other way: where the camera is allowed to influence
 * what a fragment looks like.
 *
 * Screen-space derivatives are the one mechanism by which camera orientation leaks into a
 * fragment shader. dFdx/dFdy/fwidth report how a value changes between neighbouring pixels, so
 * anything built from them moves when the view moves, even though the surface has not.
 *
 * chunk.frag used a derivative-derived normal to drive the shadow bias. At grazing viewing angles
 * the derivatives run nearly parallel, the cross product collapses, and the normal came out on the
 * wrong axis — whole faces swapped between lit and shadowed as the mouse moved. The normal now
 * comes off the mesh, where it belongs.
 *
 * Nothing about a surface's own geometry depends on the viewer, so nothing that feeds shadowing
 * may be derived from the viewer. This test exists because that is easy to reintroduce: reaching
 * for fwidth to soften an edge or dFdx to fake a normal both look locally reasonable, and the
 * damage only shows up as flicker while turning, which no unit test would otherwise catch.
 *
 * If a genuinely view-dependent effect is wanted later (mip selection, screen-space AA), it does
 * not belong in the shadow path — put it in its own shader, or narrow this test to the shadow
 * function rather than deleting it.
 */
class ShaderSourceTest {

    // Shaders whose output feeds shadowing, and so must not depend on where the camera looks.
    private static final List<String> CAMERA_INDEPENDENT_SHADERS =
            List.of("/shaders/chunk.frag", "/shaders/shadow.vert", "/shaders/shadow.frag");

    // Word boundaries so a variable innocently named "fwidthScale" is not a false positive.
    private static final Pattern DERIVATIVES =
            Pattern.compile("\\b(dFdx|dFdy|dFdxFine|dFdyFine|dFdxCoarse|dFdyCoarse|fwidth)\\s*\\(");

    /*
     * chunk.frag sizes its uniform arrays with a literal CASCADE_COUNT, because GLSL needs an array
     * size at compile time and nothing uploads one. Java owns the real number.
     *
     * Get them out of step and there is no error anywhere: the shader reads uniform slots Java
     * never filled, which default to zero, so the extra cascade silently projects everything to a
     * single point and shadows go wrong in a way that looks like a maths bug in the light matrix.
     */
    @Test
    void chunkFragCascadeCountMatchesShadowCamera() {
        Matcher m = Pattern.compile("const\\s+int\\s+CASCADE_COUNT\\s*=\\s*(\\d+)\\s*;")
                .matcher(read("/shaders/chunk.frag"));

        assertTrue(m.find(), "chunk.frag no longer declares CASCADE_COUNT");
        assertEquals(
                ShadowCamera.cascadeCount(),
                Integer.parseInt(m.group(1)),
                "chunk.frag's CASCADE_COUNT and ShadowCamera.cascadeCount() have drifted apart");
    }

    @Test
    void shadowPathShadersUseNoScreenSpaceDerivatives() {
        for (String path : CAMERA_INDEPENDENT_SHADERS) {
            String source = stripComments(read(path));
            Matcher m = DERIVATIVES.matcher(source);
            if (m.find()) {
                fail(path + " uses " + m.group(1) + "(), a screen-space derivative. That makes the "
                        + "result depend on where the camera is looking, and this shader feeds shadowing. "
                        + "See the note at the top of ShaderSourceTest.");
            }
        }
    }

    // The comments in these files discuss the derivatives they deliberately avoid, so a raw
    // substring search over the whole file would flag its own explanation.
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String read(String path) {
        try (InputStream in = ShaderSourceTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "shader not on the classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
