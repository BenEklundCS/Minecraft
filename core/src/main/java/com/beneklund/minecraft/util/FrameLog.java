package com.beneklund.minecraft.util;

import java.util.Arrays;

public class FrameLog {
    public static final float P0 = 0.0f;
    public static final float P50 = 0.50f;
    public static final float P99 = 0.99f;
    public static final float P100 = 1.00f;
    public static final int FRAME_HISTORY = 300;

    private int count = 0;
    private int writeIndex = 0;
    private FrameSample[] samples;

    record FrameSample(float ms) {}
    ;

    public FrameLog(int capacity) {
        samples = new FrameSample[capacity];
    }

    public void record(float millis) {
        FrameSample sample = new FrameSample(millis);
        samples[writeIndex] = sample;
        writeIndex = (writeIndex + 1) % samples.length;
        count = Math.min(count + 1, samples.length);
    }

    public int count() {
        return count;
    }

    public void reset() {
        samples = new FrameSample[samples.length];
        count = 0;
        writeIndex = 0;
    }

    public float percentile(float quantile) {
        if (count == 0) return 0.0f;
        double[] percentiles = Arrays.stream(samples)
                .limit(count)
                .mapToDouble(FrameSample::ms)
                .sorted()
                .toArray();
        int idx = Math.clamp((int) (count * quantile), 0, count - 1);
        return (float) percentiles[idx];
    }

    public float max() {
        return (float) Arrays.stream(samples)
                .limit(count)
                .mapToDouble(FrameSample::ms)
                .max()
                .orElse(0.0f);
    }
}
