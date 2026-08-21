#version 330 core
#include "/shaders/lib/sky.glsl"
#include "/shaders/lib/noise.glsl"

/*
 * The cloud volume, raymarched. Runs on its own fullscreen triangle into a reduced-resolution
 * buffer before the scene is drawn; sky.frag samples the result and composites it.
 *
 * Output is premultiplied: rgb is the radiance this volume scattered toward the eye, alpha is how
 * much of whatever sits behind it survived.
 *
 * Three fields stacked, coarsest first, which is the whole idea:
 *
 *   weather  - kilometre-scale. Decides what KIND of cloud a region has, how much of it, and what
 *              altitude it hangs at. This is why one valley gets a low overcast and the ridge
 *              beyond it gets towers.
 *   shape    - the outline of an individual cloud, domain-warped so it does not read as noise.
 *   detail   - 3D, erodes the outline into billows.
 *
 * A single field cannot do this. Noise is statistically identical everywhere by construction, so
 * one field of it gives one kind of cloud at one altitude forever, however many octaves it has.
 */
in vec3 vViewDir;

uniform float uTime;
uniform vec3 uCameraPos;

out vec4 FragColor;

/*
 * The volume the march covers. Everything any weather can produce has to fit inside it, because
 * this is the box the ray is clipped against before a single sample is taken.
 *
 * The floor is 200 because terrain cannot reach it: the tallest biome is MOUNTAINS at
 * baseHeight 95 + amplitude 90 = 185 (Biome.java:29). That 15-block gap is what lets clouds hang
 * on the ridgelines and well below the eye from a peak, without this shader needing to know
 * anything about terrain - nothing can ever occlude a cloud from inside the deck. Drop the floor
 * under 185 and mountains start rendering behind clouds they should be standing in front of, and
 * fixing that means sampling scene depth, which means this pass has to move after the opaque one.
 */
const float CLOUD_SLAB_BOTTOM = 190.0;
const float CLOUD_SLAB_TOP = 460.0;

// Where a deck's underside can sit, in blocks. The spread is the point - a fixed altitude is what
// made the old layer read as a ceiling rather than as weather.
const float CLOUD_BASE_LOW = 200.0;
const float CLOUD_BASE_HIGH = 300.0;

// How thick a deck is, by type. A stratus sheet is thin and wide; a thunderhead is a tower, and
// 130 blocks of it is the thing you fly around rather than through.
//
// Both numbers are held against CLOUD_FEATURE_BLOCKS rather than chosen on their own: a cloud
// 180 wide and 45 thick is a flat-bottomed puff, and 180 wide by 130 is a tower. Set thickness
// independently of width and you get a pancake or a pillar, and neither reads as a cloud.
const float CLOUD_THICKNESS_STRATUS = 45.0;
const float CLOUD_THICKNESS_TOWER = 130.0;

// Region size for the weather field: the scale at which the sky changes its mind about what the
// weather is. Held at roughly eight clouds across - enough that a region reads as "it is overcast
// over there and clear here" rather than as one cloud's own size, and small enough that a minute
// of flying crosses one. Tie it to CLOUD_FEATURE_BLOCKS if you change that again; the ratio is
// what matters, not the number.
const float CLOUD_WEATHER_BLOCKS = 1500.0;
const float CLOUD_WEATHER_SCALE = 1.0 / CLOUD_WEATHER_BLOCKS;

// One noise unit for the shape field spans this many blocks, so it sets how big a single cloud is.
// An individual puff comes out at roughly a third of this - the wavelength is the distance between
// cloud and gap, not the cloud - so 180 gives a cloud about 60-80 blocks across and a few hundred
// from one to the next.
//
// The history here is the whole lesson. It was 1400, then 600, and both were sized against the
// *world* - render distance, mountain width - on the theory that a cloud needs something its own
// size to be judged against. That was backwards. A cloud judged against a mountain range IS a
// mountain range, and at 600 one puff was 200-300 blocks wide: wider than any terrain feature in
// the world, taking twelve seconds to cross at FLY_SPEED, and reading as continental rather than
// as weather. Clouds are small things seen from far away, and the size that reads as a cloud is
// the size that crosses your view in a couple of seconds when you fly at it.
const float CLOUD_FEATURE_BLOCKS = 180.0;
const float CLOUD_SCALE = 1.0 / CLOUD_FEATURE_BLOCKS;

// How far the shape lookup is displaced by its own noise, in feature units. This is what turns a
// blobby threshold into something with wisps and torn edges. Past ~1.0 the field folds over itself.
const float CLOUD_WARP = 0.45;

// The 3D field that erodes the shape into billows. Kept at a fifteenth of CLOUD_FEATURE_BLOCKS -
// it is the ratio that reads as billows, not the absolute size, so this tracks the cloud size
// rather than being tuned on its own. Three octaves take the finest detail to about 3 blocks,
// which is roughly a cloud's own edge resolution at this scale.
const float CLOUD_DETAIL_BLOCKS = 12.0;
const float CLOUD_DETAIL_SCALE = 1.0 / CLOUD_DETAIL_BLOCKS;

// Below this much tilt away from level the deck is fully faded out. sin(4 degrees) = 0.070, so it
// dissolves over the last few degrees before the horizon instead of ending at a hard line.
const float CLOUD_HORIZON_FADE = 0.07;

// Over how many blocks of altitude the camera counts as going from outside the deck to inside it.
// Anything smaller than a deck's own thickness and the transition is still effectively a switch.
const float CLOUD_INSIDE_MARGIN = 60.0;

// The middle of the coverage range, and how far either side of it the weather field is allowed to
// push, so the map genuinely contains clear sky and genuinely contains overcast - the difference
// between weather and a setting.
//
// Higher than it looks like it should be, because the field this is thresholded against is not
// centred where the arithmetic assumes. cloudDensityAt compares fbm output to 1.0 - coverage, which
// is only "half the sky" if the fbm averages 0.5. Ported to the CPU and sampled over 490k points,
// the 2-octave weather fbm averages 0.423 and the 4-octave warped shape fbm averages 0.486 - value
// noise put through a quintic fade is not a symmetric distribution. Every threshold here has been
// running about 0.07 stingier than the constant claimed. This absorbs that; do not "fix" it back
// to 0.6 without re-measuring the field first.
const float CLOUD_COVERAGE = 0.75;
const float CLOUD_COVERAGE_VARIANCE = 0.9;

// Width of the soft rim, in fbm units. Below about 0.05 the edges alias and crawl as the layer
// drifts. 0.2 was most of why the sky came out sparse: full density needed the fbm above
// 1 - coverage + 0.2, which a 4-octave field almost never reaches, so nearly every cloud was rim
// and the erosion below then deleted it. 0.12 leaves a soft edge and still has a core behind it.
const float CLOUD_SHARPNESS = 0.12;

// Towers are tall, not wide. Without this the coverage that gives a pleasant scattering of cumulus
// gives a solid ceiling once the profile stretches it to 130 blocks thick.
const float CLOUD_TOWER_NARROW = 0.55;

// Where the type field crosses from sheet to puffy to tower. Most of the map should be the middle
// of that range, so the ends are set well apart and the towers stay an event.
const float CLOUD_TYPE_LOW = 0.38;
const float CLOUD_TYPE_HIGH = 0.72;

// Blocks per second the weather slides, and the bearing it slides along. Applied in blocks and
// scaled afterwards, so changing the cloud size does not change the wind speed.
const float CLOUD_DRIFT_SPEED = 3.0;
const vec2 CLOUD_DRIFT_DIR = vec2(1.0, 0.3);

// How hard the 3D field eats into the shape. Weighted by (1 - shape) at the call site, so this is
// the erosion at the very edge of a cloud and nothing at all in its core.
//
// 0.55 was too much for a field whose shape rarely got above 0.3: the subtraction there came to
// 0.23 and took the whole cloud with it, which is the other half of why the sky was empty above
// 15 degrees of elevation. Erosion is meant to tear edges, not decide whether a cloud exists.
const float CLOUD_EROSION = 0.35;

// Extinction per unit density per block. At the density a core reaches (~0.8) this puts
// transmittance through 60 blocks of it at exp(-2.4) = 0.09 - opaque enough to hide the sun,
// translucent at the rim. The first number to reach for if clouds come out as haze.
const float CLOUD_EXTINCTION = 0.05;

// The step budget. Not the step size any more - that is CLOUD_STEP_NEAR and it is measured in
// blocks - so this is only the point at which a ray gives up. A ray that leaves the slab first
// breaks on t >= tExit and never spends them.
const int CLOUD_STEPS = 96;

/*
 * Step length in blocks: CLOUD_STEP_NEAR at the eye, growing by CLOUD_STEP_FALLOFF per block of
 * distance. A function of how far away the sample is and of nothing else.
 *
 * It used to be a fraction of the span, and that was the second way the camera got into the field.
 * span is the slice of slab a ray crosses, so it runs from ~300 blocks looking steeply up to the
 * full CLOUD_MARCH_DISTANCE near the horizon: the same cloud was sampled every 10 blocks from one
 * altitude and every 150 from another. A 45-block stratus deck sampled at 150 is found by one step
 * or by none, which is a cloud that thins and pops as you move rather than one that stays put.
 *
 * Anchored to distance, a cloud is sampled the same way from anywhere you can see it at the same
 * range. Near steps stay fine enough to resolve a deck's thickness; far ones grow because a cloud
 * 2 km out is a silhouette and there is no resolution left in which to see the difference.
 */
const float CLOUD_STEP_NEAR = 8.0;
const float CLOUD_STEP_FALLOFF = 1.0 / 500.0;

/*
 * Past this, the light march is dropped for a guess.
 *
 * The march is the most expensive thing in the shader by a wide margin, and a distant cloud is a
 * silhouette with a bright side - there is no resolution left in which to see whether its
 * self-shadowing was integrated or approximated. The guess is height through the deck, which is
 * what the march mostly recovers anyway: the top is lit, the bottom is not.
 *
 * Has to stay well inside CLOUD_MARCH_DISTANCE or it does nothing at all. At 1200 against a march
 * of 600 the cutoff sat beyond the far end of every ray, so every cloud sample in the frame paid
 * for a full four-step light march and the branch that exists to make horizon views affordable
 * never once taken. Half the march distance keeps the near field integrated and the far field cheap.
 */
const float CLOUD_LIGHT_MAX_DISTANCE = 300.0;

/*
 * How much further a step that found nothing advances than one that found cloud.
 *
 * Measured: at CLOUD_STEPS = 96 and half resolution this pass cost 75 ms a frame, 63 fps looking
 * down to 11 looking up. Roughly half of every ray's samples come back zero, and a zero costs the
 * same as any other sample - so empty space gets strided through and only cloud gets the fine step.
 *
 * The price is that a wisp thinner than one coarse step can be missed, which makes thin edges
 * slightly thinner. Past about 3.0 that stops being subtle and cloud bottoms start to shear.
 */
const float CLOUD_EMPTY_STRIDE = 2.0;

// The cap, and the single most load-bearing number in this file for how the sky reads.
//
// It is not really a draw distance, it is the horizon-to-zenith contrast control. A ray a few
// degrees above level crosses this whole distance through the deck and integrates every cloud
// along it; a ray at 20 degrees crosses only thickness/sin(20) and integrates about one. Measured
// against a 3000-step reference march at 1600: opacity 0.44 at 5 degrees of elevation and 0.00 at
// 20. A hundred to one, which is not a sky with clouds in it - it is a bright ribbon welded to the
// horizon with clear blue above, and no amount of shape or coverage tuning fixes that, because it
// is path length and nothing else.
//
// It was cut to 600 to fight that ratio and that was the wrong lever - what actually fixed the
// ratio was correcting CLOUD_COVERAGE for the fbm's true mean. All the short range did was delete
// clouds, and from low altitude it deleted nearly all of them: the deck sits at 200..300, so from
// the ground it is only close straight overhead and is 500-1500 blocks away at any oblique angle.
// Measured at 900, a camera at y=80 saw opacity 0.000 at 5 degrees of elevation and 0.081 at 15 -
// an empty sky. At 2500 the same camera sees 0.607 and 0.105. Anything past ~2500 adds almost
// nothing (0.759 at 5000), so this is where the curve flattens.
const float CLOUD_MARCH_DISTANCE = 2500.0;

// Where along the march the range fade begins, as a fraction of it. 0.55 gives 400 blocks to
// dissolve over - a cloud takes about eight seconds of FLY_SPEED to cross that, which is slow
// enough that nothing is seen to appear.
const float CLOUD_RANGE_FADE_START = 0.55;

// The light march: how far toward the sun to look for something casting onto this point, and in
// how many steps. Sized to reach up the side of a tower without leaving it - 130 blocks of tower,
// so 140. At 300 it walked clean out of the top of a cloud and spent most of its samples on empty
// air, which cost frame time and returned less shadow than it should have.
const float CLOUD_LIGHT_DISTANCE = 140.0;
const int CLOUD_LIGHT_STEPS = 4;

const int CLOUD_SHAPE_OCTAVES = 4;
const int CLOUD_DETAIL_OCTAVES = 3;
// The light march only has to answer "is there cloud between here and the sun", and it asks that
// up to CLOUD_STEPS x CLOUD_LIGHT_STEPS times per pixel. Two octaves and no domain warp.
const int CLOUD_LIGHT_OCTAVES = 2;

// Direct light is not the only light reaching the shadowed side of a cloud - most of what gets
// there has bounced inside the volume several times first, and modelling that properly is a
// research problem. A floor under the light-march transmittance stands in for it, so undersides
// come out grey rather than black. Raise it and clouds go flat; drop it and they go to silhouette.
const float CLOUD_MULTI_SCATTER = 0.12;
// And a matching discount on the direct term, for the same reason.
const float CLOUD_LIGHT_ABSORPTION = 0.35;

// Linear radiance, the same scale skyRadiance and the sun disc use. Derived from the measured 15.4
// of a fully lit terrain face scaled by albedo ratio: 15.4 * (0.85 / 0.30) = 43.6. Under
// BLOOM_THRESHOLD = 50 (PostProcessor.java:48) for the body of a cloud; the phase function pushes
// the rim facing the sun past it on purpose, and that is the silver lining.
const float CLOUD_LIT_RADIANCE = 44.0;

// The sky lights the volume too, from every direction at once. CLOUD_AMBIENT is the fraction of
// zenith radiance a point in the volume sees; CLOUD_AMBIENT_BASE is how much is left at the bottom
// of a deck, where most of the visible hemisphere is more cloud. The gap between them is what
// makes a thunderhead's underside dark and its crown bright.
const float CLOUD_AMBIENT = 0.55;
const float CLOUD_AMBIENT_BASE = 0.35;

// Air is about 0.76: strongly forward-scattering, which is why haze glows toward the sun and stays
// dim away from it. Same value GG-8 Stage 3's raymarch uses - keep them equal.
const float CLOUD_SCATTER_G = 0.76;
const float PI = 3.14159265;

// See cloudPhase. The floor is the isotropic part; the lobe is how much Henyey-Greenstein rides on
// top of it.
const float CLOUD_PHASE_FLOOR = 0.8;
const float CLOUD_PHASE_LOBE = 1.5;

// Below this much surviving light no remaining step can change the pixel by anything the display
// could show - and the steps still to come are the expensive ones, because being this opaque means
// being inside cloud, where every step pays for a light march.
const float CLOUD_MIN_TRANSMITTANCE = 0.03;

/*
 * What the sky is doing over this column of the world. Sampled once per view step and reused by
 * the light march under it - the field is kilometre-scale, and the light march is 300 blocks long,
 * so it cannot meaningfully change across one.
 */
struct Weather {
    float coverage; // [0,1], how much of this region wants to be cloud
    float type; // 0 = flat sheet, 1 = tower
    float base; // blocks, the underside of the deck here
    float thickness; // blocks
};

// Applied in blocks and scaled afterwards, never added to an already-scaled coordinate: wind speed
// is a world quantity, and adding to noise-space uv would tie the speed to the cloud size.
vec2 cloudDriftBlocks() {
    return normalize(CLOUD_DRIFT_DIR) * CLOUD_DRIFT_SPEED * uTime;
}

/*
 * Three independent low-frequency samples. Independent matters: offset each lookup far enough that
 * the fields do not correlate, or every overcast region is also every tower region and the sky
 * only ever has one thing going on at a time.
 */
Weather weatherAt(vec2 xz) {
    vec2 uv = (xz + cloudDriftBlocks()) * CLOUD_WEATHER_SCALE;

    // Two octaves and then one, and that is not a compromise: at CLOUD_WEATHER_BLOCKS = 1500 the
    // second octave already has a 750-block feature - several clouds across - and the third would
    // be describing detail at the size of a single cloud, which is the shape field's job and not
    // this one's. This runs on every step of every ray, cloud or not, which makes
    // it the most-executed code in the shader - it was 7 octaves and that alone was most of the
    // 75 ms.
    float amount = fbm(uv, 2);
    float kind = fbm(uv + vec2(93.1, 17.7), 1);
    // Two thirds the frequency of the others, so the altitude the deck sits at drifts across a
    // region rather than changing with it - a deck that stepped altitude at every weather boundary
    // would read as a bug, and it would be one.
    float lift = fbm(uv * 0.66 + vec2(5.5, 61.2), 1);

    Weather w;
    w.coverage = clamp(CLOUD_COVERAGE + (amount - 0.5) * CLOUD_COVERAGE_VARIANCE, 0.0, 1.0);
    w.type = smoothstep(CLOUD_TYPE_LOW, CLOUD_TYPE_HIGH, kind);
    w.base = mix(CLOUD_BASE_LOW, CLOUD_BASE_HIGH, lift);
    w.thickness = mix(CLOUD_THICKNESS_STRATUS, CLOUD_THICKNESS_TOWER, w.type);
    return w;
}

/*
 * The vertical profile, as four control points on the [0,1] height through the deck: fade in from
 * x to y, fade out from w back to z.
 *
 * One shape per cloud type, interpolated rather than switched, because the type field is
 * continuous and a switch would put a visible seam wherever it crossed a threshold.
 *
 * The three shapes are what actually distinguishes the types, more than thickness does:
 *   stratus  - on almost immediately, off almost immediately. A sheet with a flat top and bottom.
 *   cumulus  - flat base, mass in the lower half, rounded crown tapering out by three quarters.
 *   tower    - flat base and full height, so the mass reaches the top of a 380-block deck.
 */
vec4 cloudProfile(float type) {
    vec4 stratus = vec4(0.00, 0.10, 0.55, 0.75);
    vec4 cumulus = vec4(0.00, 0.18, 0.45, 0.80);
    vec4 tower = vec4(0.00, 0.08, 0.80, 1.00);
    return type < 0.5 ? mix(stratus, cumulus, type * 2.0) : mix(cumulus, tower, (type - 0.5) * 2.0);
}

/*
 * Density at a world point, given the weather over it. This is the function the whole shader is an
 * integral of, and it runs more often than anything else here - every early-out matters.
 *
 * warp of 0 skips the domain warp entirely, which is two fbm calls; the light march passes 0.
 */
float cloudDensityAt(vec3 p, Weather w, int shapeOctaves, int detailOctaves, float warp) {
    float h = (p.y - w.base) / w.thickness;
    if (h < 0.0 || h > 1.0) return 0.0;

    vec4 profile = cloudProfile(w.type);
    float gradient = smoothstep(profile.x, profile.y, h) * smoothstep(profile.w, profile.z, h);
    if (gradient <= 0.0) return 0.0;

    vec2 uv = (p.xz + cloudDriftBlocks()) * CLOUD_SCALE;
    float outline = warp > 0.0 ? fbmWarped(uv, shapeOctaves, warp) : fbm(uv, shapeOctaves);

    // Towers are tall, not wide - see CLOUD_TOWER_NARROW.
    float coverage = w.coverage * mix(1.0, CLOUD_TOWER_NARROW, w.type);
    float shape = smoothstep(1.0 - coverage, 1.0 - coverage + CLOUD_SHARPNESS, outline) * gradient;
    // The early-out that pays: roughly half of every ray's steps land in clear sky, and each one
    // skips an fbm3 and - back in main - a whole light march.
    if (shape <= 0.0) return 0.0;

    // 3D detail subtracted from the shape rather than multiplied into it, and weighted by
    // (1 - shape) so it bites hardest at the rim and not at all in the core. Multiply instead and
    // the whole cloud goes lacy all the way through, which reads as smoke.
    vec2 drift = cloudDriftBlocks();
    float detail = fbm3(vec3(p.x + drift.x, p.y, p.z + drift.y) * CLOUD_DETAIL_SCALE, detailOctaves);
    return max(shape - CLOUD_EROSION * (1.0 - detail) * (1.0 - shape), 0.0);
}

/*
 * The fraction of sunlight reaching this point through whatever cloud stands between it and the
 * sun. This is the term that makes the volume read as lit from one side, and where most of the
 * frame time goes.
 *
 * uSunDirection points toward the sun, so stepping along it is stepping toward the light.
 */
float cloudLightTransmittance(vec3 p, Weather w) {
    float stepSize = CLOUD_LIGHT_DISTANCE / float(CLOUD_LIGHT_STEPS);
    float optical = 0.0;
    for (int i = 0; i < CLOUD_LIGHT_STEPS; i++) {
        // Half a step in, so the first sample lands in the middle of the first segment rather than
        // on the point whose density the caller already knows.
        vec3 s = p + uSunDirection * (stepSize * (float(i) + 0.5));
        optical += cloudDensityAt(s, w, CLOUD_LIGHT_OCTAVES, CLOUD_LIGHT_OCTAVES, 0.0) * stepSize;
    }
    return max(exp(-optical * CLOUD_EXTINCTION * CLOUD_LIGHT_ABSORPTION), CLOUD_MULTI_SCATTER);
}

/*
 * How much of the sun's light this bearing scatters toward the eye.
 *
 * Not the phase function on its own, which is what a single scattering event does. That falls from
 * 2.4 looking into the sun to 0.006 looking away from it, and a cloud lit by only that is black on
 * three sides. Light leaving the far side of a cloud has scattered many times and has no memory of
 * which way it came in, so it emerges isotropically - CLOUD_PHASE_FLOOR stands in for that. The
 * lobe on top is the part that has not forgotten, and it is what puts the bright rim on a cloud
 * standing in front of the sun.
 */
float cloudPhase(float cosAngle) {
    float gg = CLOUD_SCATTER_G * CLOUD_SCATTER_G;
    float hg = (1.0 - gg) / (4.0 * PI * pow(1.0 + gg - 2.0 * CLOUD_SCATTER_G * cosAngle, 1.5));
    return CLOUD_PHASE_FLOOR + CLOUD_PHASE_LOBE * hg;
}

/*
 * Where this ray enters and leaves the slab. Two ray-plane solves, t = (h - origin.y) / dir.y, run
 * once at each face.
 *
 * They are sorted rather than assigned, because which one is the entry depends on where the ray
 * points: from below the deck it meets the floor first, from above it meets the ceiling first.
 *
 * The level-ray branch checks the camera's height rather than always bailing, because level
 * *inside* the slab is the one case with no plane crossing that still has cloud in front of the
 * eye - which is exactly what flying into the deck produces.
 */
bool cloudSlab(vec3 dir, out float tEnter, out float tExit) {
    tEnter = 0.0;
    tExit = 0.0;

    if (abs(dir.y) < 1e-4) {
        if (uCameraPos.y < CLOUD_SLAB_BOTTOM || uCameraPos.y > CLOUD_SLAB_TOP) return false;
        tExit = CLOUD_MARCH_DISTANCE;
        return true;
    }

    float t0 = (CLOUD_SLAB_BOTTOM - uCameraPos.y) / dir.y;
    float t1 = (CLOUD_SLAB_TOP - uCameraPos.y) / dir.y;
    tEnter = min(t0, t1);
    tExit = max(t0, t1);

    // Clamped to zero rather than rejected, because a camera inside the slab has a genuinely
    // negative entry - the slab starts behind it. Rejecting on tEnter < 0 deletes the clouds at
    // the moment you fly into them.
    tEnter = max(tEnter, 0.0);
    if (tExit <= tEnter) return false;

    // Measured from the CAMERA, not from tEnter, and that is the whole point. Anchoring the cap
    // to the slab entry makes the rendered slice of the deck depend on how far away the slab entry
    // is - which depends on the camera's altitude and the ray's pitch. From y=150 a 5 degree ray
    // entered the slab 460 blocks out and rendered 460..1060; from y=195, inside the slab, the same
    // ray entered at 0 and rendered 0..600. Climbing through CLOUD_SLAB_BOTTOM therefore teleported
    // the visible window half a kilometre inward and swapped every cloud in the frame for a
    // different one, in a single frame. Anchored here, the rendered volume is a fixed sphere around
    // the camera that slides smoothly as the player moves, which is the only way a cloud can stay
    // the same cloud while you fly at it.
    tExit = min(tExit, CLOUD_MARCH_DISTANCE);
    if (tExit <= tEnter) return false;
    return true;
}

void main() {
    vec3 dir = normalize(vViewDir);

    float tEnter;
    float tExit;
    if (!cloudSlab(dir, tEnter, tExit)) {
        // Nothing scattered, everything behind it survives. Written rather than skipped because
        // this pass does not clear its target - every pixel of the triangle writes its own answer.
        FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // A grazing ray from outside the slab crosses hundreds of noise units per step and aliases into
    // a crawling band, which is also roughly where a real deck disappears into haze. From inside
    // the slab there is nothing to fade: a level ray there is looking down the length of it.
    // Ramped rather than switched. This used to be a bool on the same CLOUD_SLAB_BOTTOM the march
    // window was keyed to, so crossing that altitude turned the horizon fade off in one frame at
    // the same instant the window jumped - two discontinuities stacked on the same threshold, which
    // is most of why flying up through the deck looked like the sky being replaced rather than
    // entered.
    float insideness = smoothstep(CLOUD_SLAB_BOTTOM - CLOUD_INSIDE_MARGIN, CLOUD_SLAB_BOTTOM, uCameraPos.y)
        * smoothstep(CLOUD_SLAB_TOP + CLOUD_INSIDE_MARGIN, CLOUD_SLAB_TOP, uCameraPos.y);
    float fade = mix(smoothstep(0.0, CLOUD_HORIZON_FADE, abs(dir.y)), 1.0, insideness);
    if (fade <= 0.0) {
        FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float span = tExit - tEnter;
    // In blocks, and recomputed from the sample's own distance at the bottom of the loop.
    float stepSize = CLOUD_STEP_NEAR;
    // A fixed per-pixel fraction, applied to EVERY step rather than only to the first - see the
    // sample point in the loop. Without any of it, every ray in the frame samples the same set of
    // distances and the volume breaks into shells at them. Fixed per pixel rather than per frame
    // because there is no temporal filter here to average a moving pattern out, and a crawling
    // pattern is worse than a static one.
    //
    // Offsetting only the entry point, which is what this used to do, dithers the near field and
    // nothing else: the offset was one *initial* step wide while the far steps were many times
    // that, so the far half of every ray was effectively undithered, and a 45-block deck seen at
    // range came out as a handful of hard horizontal slabs.
    float jitter = hash21(gl_FragCoord.xy);
    float t = tEnter;

    // All three hoisted out of the loop: constant along the ray, and skyRadiance in particular is
    // the most expensive thing in this file.
    vec3 sunLight = sunlightColor() * CLOUD_LIT_RADIANCE;
    vec3 skyAmbient = skyRadiance(vec3(0.0, 1.0, 0.0)) * CLOUD_AMBIENT;
    float phase = cloudPhase(dot(dir, uSunDirection));

    vec3 scattered = vec3(0.0);
    float transmittance = 1.0;

    for (int i = 0; i < CLOUD_STEPS; i++) {
        if (t >= tExit) break;

        // How far along the ray this sample stands, held because t moves on below and the light
        // cutoff is a question about where the sample is, not about where the next one will be.
        //
        // Jittered by a fraction of THIS step, so the offset grows with the step it sits in. Taking
        // the sample at a random point inside the segment rather than always at its start is also
        // the less biased estimate of the segment's average density, which is what the Beer-Lambert
        // integral below wants - the dithering is the visible half of a change that is correct
        // anyway.
        float sampleT = t + stepSize * jitter;
        vec3 p = uCameraPos + dir * sampleT;
        // Sampled at the column the sample stands in, not interpolated between the ray's two ends.
        //
        // The endpoint blend made the density field a function of the camera. tEnter and tExit move
        // with altitude and pitch, so a fixed world point got a different coverage, base and
        // thickness depending on where you were standing - from y=400 its weather was 0.29 of the
        // camera column and 0.71 of one 840 blocks out, from y=200 it was 0.76 and 0.24 of one 2491
        // blocks out. Those are unrelated blends, and cloudDensityAt hard-gates on
        // (p.y - base) / thickness, so a few tens of blocks of drift in base deletes the cloud
        // outright. That is why one cloud dissolved while its neighbours survived as you descended,
        // and why the whole population changed as you flew: moving the camera rewrote the sky.
        //
        // Weather is a property of the column, not of the ray that happens to be crossing it.
        Weather w = weatherAt(p.xz);
        float density = cloudDensityAt(p, w, CLOUD_SHAPE_OCTAVES, CLOUD_DETAIL_OCTAVES, CLOUD_WARP);

        // Thinned toward the far edge of the march, because that edge is now close enough to see.
        // tExit truncates: a cloud a block inside CLOUD_MARCH_DISTANCE draws in full and one a
        // block outside does not exist. That was invisible while the cap was 1600 blocks out and
        // whatever sat there was a few pixels tall; at 900 it is a wall a few seconds of flying
        // away, and crossing it pops a whole population of clouds into the frame at once. Fading
        // the density rather than the composite so a cloud entering range grows out of nothing
        // instead of arriving at full opacity and then thickening.
        density *= 1.0 - smoothstep(CLOUD_MARCH_DISTANCE * CLOUD_RANGE_FADE_START, CLOUD_MARCH_DISTANCE, sampleT);

        // The stride, and why t advances here rather than at the top of the loop: how far to move
        // is not known until this sample has answered. Nothing to integrate either way - density
        // is zero, so the segment contributes nothing however long it is.
        if (density <= 0.0) {
            t += stepSize * CLOUD_EMPTY_STRIDE;
            stepSize = CLOUD_STEP_NEAR * (1.0 + sampleT * CLOUD_STEP_FALLOFF);
            continue;
        }
        // Held before the growth is applied. The Beer-Lambert integral below is over the segment
        // this sample actually stands for, and with growing steps that is not the same number as
        // the next step's length.
        float segment = stepSize;
        t += segment;
        stepSize = CLOUD_STEP_NEAR * (1.0 + sampleT * CLOUD_STEP_FALLOFF);

        float h = clamp((p.y - w.base) / w.thickness, 0.0, 1.0);
        // See CLOUD_LIGHT_MAX_DISTANCE. The far branch is a guess at what the near branch would
        // have returned, and it is the one that keeps a horizon view affordable — near the horizon
        // almost every step of almost every pixel is beyond the cutoff.
        float lit = sampleT < CLOUD_LIGHT_MAX_DISTANCE
            ? cloudLightTransmittance(p, w)
            : mix(CLOUD_MULTI_SCATTER, 1.0, h);
        vec3 source = sunLight * lit * phase + skyAmbient * mix(CLOUD_AMBIENT_BASE, 1.0, h);

        /*
         * Cloud droplets scatter essentially everything they intercept and absorb almost none of
         * it, so the scattering coefficient equals the extinction - which is what collapses the
         * integral of source * scattering * transmittance across this segment to the closed form
         * below. Integrating properly rather than accumulating source * density * segment is what
         * keeps the result the same when the step size changes - and it changes with every bearing
         * and now with every step as well.
         */
        float sampleTransmittance = exp(-density * CLOUD_EXTINCTION * segment);
        scattered += transmittance * source * (1.0 - sampleTransmittance);
        transmittance *= sampleTransmittance;

        if (transmittance < CLOUD_MIN_TRANSMITTANCE) break;
    }

    // Fading the composite rather than the density: fading density would thin the cloud out and
    // change what colour the thinner version comes back as, where this dissolves the whole result
    // toward "no cloud here".
    FragColor = vec4(scattered * fade, mix(1.0, transmittance, fade));
}
