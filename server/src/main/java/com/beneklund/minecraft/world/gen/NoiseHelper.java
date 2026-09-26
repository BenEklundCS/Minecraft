package com.beneklund.minecraft.world.gen;

/**
 * Fractal Brownian motion (fBm): octaves of {@link OpenSimplex2} summed at rising frequency and
 * falling amplitude.
 *
 * <p>One noise sample is a smooth, blobby field with features about {@code 1 / scale} blocks
 * across. Each further octave doubles the frequency (lacunarity 2) and multiplies the amplitude
 * by {@code persistence}, so the first octave sets the large shapes and later ones add
 * progressively smaller, weaker detail. The sum is divided by the total amplitude, which keeps
 * the result in [-1, 1] for any octave count.
 *
 * <p>Scales used by {@code WorldGenerator}: 0.002 for continental shape, 0.008 for erosion and
 * hilliness, 0.04 for surface detail and cave carving.
 *
 * @see <a href="https://iquilezles.org/articles/fbm/">Inigo Quilez: fBm</a>
 * @see <a href="https://www.redblobgames.com/maps/terrain-from-noise/">Red Blob Games: Making maps
 *     with noise functions</a>
 * @see <a href="https://github.com/KdotJPG/OpenSimplex2">KdotJPG: OpenSimplex2</a>
 */
public class NoiseHelper {
    public static final double RIDGE_PEAK = 1.0;

    /**
     * 2D fBm in [-1, 1], for height maps and other surface fields.
     *
     * @param seed noise seed; callers add a per-layer offset so layers decorrelate
     * @param octaves number of octaves summed
     * @param persistence amplitude multiplier per octave; 0.5 halves each octave's contribution
     * @param scale frequency of the first octave, in cycles per block
     */
    public double noise2(long seed, double x, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = scale;
        double maxAmplitude = 0;

        for (int i = 0; i < octaves; i++) {
            total += OpenSimplex2.noise2(seed, x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }

        return total / maxAmplitude;
    }

    /**
     * 3D fBm in [-1, 1], for volumetric features such as caves. Parameters as {@link #noise2}.
     *
     * <p>Samples {@link OpenSimplex2#noise3_ImproveXZ} with Y as the vertical axis. That variant
     * rotates the lattice so horizontal (XZ) slices have the best visual isotropy, which its
     * author recommends for Y-up 3D terrain.
     */
    public double noise3(long seed, double x, double y, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = scale;
        double maxAmplitude = 0;

        for (int i = 0; i < octaves; i++) {
            total += OpenSimplex2.noise3_ImproveXZ(seed, x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }

        return total / maxAmplitude;
    }

    /** 2D fBm passed through {@link #ridge}: sharp crests where the fBm crosses zero. */
    public double ridged2(long seed, double x, double z, int octaves, double persistence, double scale) {
        return ridge(noise2(seed, x, z, octaves, persistence, scale));
    }

    /**
     * Folds noise at zero into a ridge: maps [-1, 1] to [-1, 1] with the peak at {@code n = 0} and
     * a sharp crease there. Applied once to the fBm sum, so the crests come from the sum's zero
     * crossings; Musgrave's ridged multifractal instead folds each octave before summing.
     *
     * @see <a href="https://www.redblobgames.com/maps/terrain-from-noise/#ridged">Red Blob Games:
     *     Ridged noise</a>
     */
    public static double ridge(double n) {
        return (RIDGE_PEAK - Math.abs(n)) * 2.0 - 1.0;
    }

    /** Maps noise from [-1, 1] to [0, 1]. */
    public double normalize(double noise) {
        return (noise + 1.0) / 2.0;
    }
}
