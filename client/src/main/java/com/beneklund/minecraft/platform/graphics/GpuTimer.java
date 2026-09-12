package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

import java.util.Arrays;

public class GpuTimer {
    private final int passCount;
    private final int[] queries; // passCount * TIMER_ROTATION query names
    private final long[] writtenFrame; // which frame last wrote each slot, -1

    public static final int TIMER_ROTATION = 3;

    public GpuTimer(int passCount) {
        this.passCount = passCount;
        queries = new int[passCount * TIMER_ROTATION];
        writtenFrame = new long[queries.length];
        Arrays.fill(writtenFrame, -1L);
        for (int i = 0; i < queries.length; i++) {
            queries[i] = glGenQueries();
        }
    }

    public void begin(int pass, long frame) {
        int index = queryIndex(pass, frame);
        glBeginQuery(GL_TIME_ELAPSED, queries[index]);
        writtenFrame[index] = frame;
    }

    public void end(int pass) {
        glEndQuery(GL_TIME_ELAPSED);
    }

    public void delete() {
        for (int id : queries) glDeleteQueries(id);
    }

    public long lastResultNanos(int pass, long frame) {
        long target = readableFrame(frame);
        if (target < 0) return -1; // startup, nothing written yet

        int index = queryIndex(pass, target);
        if (writtenFrame[index] != target) return -1; // this pass didn't run that frame

        if (glGetQueryObjecti(queries[index], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) {
            return -1; // driver buffered deeper than we assumed
        }
        return glGetQueryObjectui64(queries[index], GL_QUERY_RESULT);
    }

    private int queryIndex(int pass, long frame) {
        return pass * TIMER_ROTATION + slotFor(frame);
    }

    // Package-private, not private: these two are pure index arithmetic and carry the whole
    // correctness argument, so GpuTimerTest pins them without needing a GL context.
    static int slotFor(long frame) {
        return Math.floorMod(frame, TIMER_ROTATION);
    }

    static long readableFrame(long frame) {
        return frame - (TIMER_ROTATION - 1);
    }
}
