package com.beneklund.minecraft.util;

public class FrameLog {
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
}
