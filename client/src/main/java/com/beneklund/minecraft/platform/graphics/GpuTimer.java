package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

import java.util.Arrays;

/**
 * Measures GPU time per render pass with {@code GL_TIME_ELAPSED} queries, read back without
 * stalling the pipeline.
 *
 * <p>The GPU runs frames behind the CPU, so a query ended this frame has no result yet, and
 * reading it blocks until the GPU catches up. Each pass gets {@link #TIMER_ROTATION} queries used
 * round-robin by frame number: frame {@code n} writes slot {@code n mod 3} and reads the result of
 * frame {@code n - 2}, which the driver has almost always finished by then. The result is always
 * two frames old.
 *
 * <p>{@code GL_TIME_ELAPSED} queries can't nest, so passes are timed one after another, never
 * inside each other.
 *
 * @see <a href="https://wikis.khronos.org/opengl/Query_Object#Timer_queries">OpenGL Wiki: Timer
 *     queries</a>
 * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt">
 *     ARB_timer_query</a>
 */
public class GpuTimer {
    private final int passCount;
    private final int[] queries; // passCount * TIMER_ROTATION query names
    private final long[] writtenFrame; // which frame last wrote each slot, -1

    /** Queries per pass: frames in flight between writing a query and reading it back, plus one. */
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

    /** Starts timing {@code pass} in this frame's slot. Pair with {@link #end}. */
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

    /**
     * The GPU time {@code pass} took in frame {@code frame - 2}.
     *
     * @return nanoseconds, or {@code -1} during the first two frames, when the pass didn't run in
     *     that frame, or when the driver is buffering more than two frames and the result isn't
     *     available yet
     */
    public long lastResultNanos(int pass, long frame) {
        long target = readableFrame(frame);
        if (target < 0) return -1;

        int index = queryIndex(pass, target);
        if (writtenFrame[index] != target) return -1;

        if (glGetQueryObjecti(queries[index], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) {
            return -1;
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
