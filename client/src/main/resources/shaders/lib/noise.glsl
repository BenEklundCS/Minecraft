// GPU value noise for clouds. Deliberately not NoiseHelper: nothing here is saved, shared with the
// CPU, or required to be reproducible across runs — only to be stable within one.

// Five octaves: the fifth adds detail at 1/16 the base feature size, which at
// CLOUD_FEATURE_BLOCKS = 600 is about 37 blocks — roughly one pixel at the horizon and not worth
// evaluating. A sixth costs the same as the first five's detail combined and cannot be seen.
const int FBM_OCTAVES = 5;

// The constants below are the standard ones for this hash. They are not derivable — they are
// values that happen to decorrelate well, and citing them is more honest than explaining them.
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

/*
 * Smooth interpolation between the four hashed corners of one grid cell.
 *
 * The quintic 6t^5 - 15t^4 + 10t^3 rather than smoothstep's cubic: both have zero first derivative
 * at the ends, but only the quintic also has zero second derivative. Stage 2a compares coverage at
 * two nearby points, which is a numerical derivative of this field — with the cubic, the second
 * derivative jumps at every cell boundary and the lighting term shows a faint square grid.
 */
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

/*
 * Octaves summed at doubling frequency and halving amplitude, normalised back to [0,1] so a
 * threshold against it means the same thing regardless of FBM_OCTAVES.
 *
 * 2.02 rather than 2.0: an exact doubling lines every octave's grid up on the same lattice, and
 * the shared corners produce a visible square structure through the result.
 */
/*
 * Every octave is rotated as well as scaled, and that is what stops the result looking like it was
 * built on a grid.
 *
 * Value noise is defined on the integer lattice, so its features are axis-aligned - a cell is a
 * square and it looks like one. Scaling alone stacks five sets of squares on the same two axes and
 * the eye reads the alignment as blockiness however many octaves are piled up. Turning each octave
 * ~37 degrees means no two octaves share an axis and there is no direction for the eye to find.
 *
 * The matrix is a rotation scaled by 2.02: one multiply does both, and 2.02 rather than an exact
 * doubling because exact doubling relands every octave on the same lattice points.
 */
const mat2 FBM_STEP = mat2(1.616, 1.212, -1.212, 1.616);

float fbm(vec2 p, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;

    for (int i = 0; i < octaves; i++) {
        sum += amplitude * valueNoise(p);
        total += amplitude;
        p = FBM_STEP * p;
        amplitude *= 0.5;
    }
    return sum / total;
}

// The octave count is a parameter now because the light march inside the raymarch cannot afford
// five of them. Everything that does not care still calls fbm(p) and gets FBM_OCTAVES.
float fbm(vec2 p) {
    return fbm(p, FBM_OCTAVES);
}

/*
 * The 3D hash. Deliberately not built out of hash21: a vec3 hash that degenerated to hash21 at
 * z = 0 would line the detail field up with the coverage field on one horizontal slice, and that
 * slice would read as a flat seam cut through every cloud at the same altitude.
 *
 * Same standing as hash21's constants - values that decorrelate well, not values with a derivation.
 */
float hash31(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Eight hashed corners of a cell rather than four, with the same quintic fade on all three axes -
// and for the same reason as in valueNoise, since the volume is differenced along the sun ray.
float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float c000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float c100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float c010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float c110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float c001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float c101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float c011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float c111 = hash31(i + vec3(1.0, 1.0, 1.0));

    return mix(
        mix(mix(c000, c100, u.x), mix(c010, c110, u.x), u.y),
        mix(mix(c001, c101, u.x), mix(c011, c111, u.x), u.y),
        u.z);
}

/*
 * Octave count is a parameter for the same reason as above, only more so. The view march calls
 * this once per step; the light march inside it calls it once per light step per view step, which
 * is a couple of hundred times more often for an answer that only has to say "is there cloud
 * between here and the sun".
 */
// Same rotation trick in 3D, and it matters more here: cubes are even easier to see than squares.
// A rotation about an axis that is not any of the three, scaled by 2.02.
const mat3 FBM3_STEP = mat3(
    1.400, 1.386, -0.535,
    -0.877, 1.279, 1.186,
    1.121, -0.616, 1.470);

float fbm3(vec3 p, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;

    for (int i = 0; i < octaves; i++) {
        sum += amplitude * valueNoise3(p);
        total += amplitude;
        p = FBM3_STEP * p;
        amplitude *= 0.5;
    }
    return sum / total;
}

/*
 * fBm with its input displaced by more fBm. The single most effective thing you can do to noise
 * that looks synthetic.
 *
 * Plain fBm is isotropic and statistically the same everywhere, which is why a field of it reads
 * as texture rather than as objects - no billow ever leans, nothing ever curls. Warping the lookup
 * position by a second noise field bends the whole thing, and the result grows the wisps, hooks
 * and torn edges that make a shape look like weather instead of like a heightmap.
 *
 * strength is in the same units as p, so a strength of 0.4 displaces the lookup by up to 0.4 of a
 * feature. Past about 1.0 the field folds over itself and turns to soup.
 */
float fbmWarped(vec2 p, int octaves, float strength) {
    vec2 offset = vec2(fbm(p + vec2(17.3, 4.1), 2), fbm(p + vec2(41.7, 29.2), 2));
    return fbm(p + (offset * 2.0 - 1.0) * strength, octaves);
}