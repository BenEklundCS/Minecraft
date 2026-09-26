package com.beneklund.minecraft.platform.debug;

import static com.beneklund.minecraft.util.Log.RENDER;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The debug HTTP server: streams the last rendered frame to a browser on localhost and accepts
 * commands that make a run reproducible.
 *
 * <p>Watching a problem like shadow flicker live, while changing one thing at a time, needs a
 * faster loop than screenshots. The commands turn "does that constant help" into a comparison of
 * two frames taken from the same pose at the same time of day.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Path</th><th>Does</th></tr>
 *   <tr><td>{@code /}</td><td>The debug page, {@code /debug/index.html} from the classpath.</td></tr>
 *   <tr><td>{@code /frame.png}</td><td>The latest frame; 503 until one exists.</td></tr>
 *   <tr><td>{@code /tp?x&y&z&yaw&pitch}</td><td>Teleports; omitted fields are unchanged.</td></tr>
 *   <tr><td>{@code /time?t}</td><td>Sets time of day, 0 midnight, 0.5 noon.</td></tr>
 *   <tr><td>{@code /bench}</td><td>Fixed pose, midday, frame log cleared.</td></tr>
 *   <tr><td>{@code /stats}</td><td>The stats supplier's text; 501 when unwired.</td></tr>
 * </table>
 *
 * <p>Started only when {@code debugserver.enabled} is set, and bound to 127.0.0.1.
 *
 * <p>Threading: {@link #submit} runs on the main thread and only copies bytes; one daemon worker
 * encodes the PNG, so the encode stays out of the frame. HTTP handlers run on a two-thread pool
 * and queue commands; {@link #drainCommands()} runs them on the main thread, since they touch the
 * player and the day cycle. Capture runs only while a viewer has polled {@code /frame.png} in the
 * last two seconds, at most every 100 ms, and never while an encode is in flight.
 *
 * @see <a
 *     href="https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html">
 *     JDK HttpServer</a>
 */
public class FrameStreamServer {

    // The browser polls faster than this; the limit is here so the encode worker and the render
    // thread's readPixels stay off the frame budget. 10 fps is plenty to see flicker.
    private static final long MIN_FRAME_INTERVAL_MS = 100;

    // How long after a viewer's last /frame.png request capture keeps running. Comfortably more
    // than the viewer's own poll interval, so a browser that is watching never sees a gap, and
    // short enough that closing the tab stops the cost within a couple of seconds.
    private static final long VIEWER_IDLE_TIMEOUT_MS = 2_000;
    private static final int CHANNELS = 3;

    // The benchmark viewpoint. Chosen once and then never changed - a number taken at a
    // different pose is not comparable to the numbers already written down.
    private static final float[] BENCH_POSE = {8.0f, 120.0f, -5.0f, 45.0f, -15.0f};

    // Midday. Shadows, sky and clouds are all at full cost here; dawn is cheaper and would
    // flatter the numbers.
    private static final float BENCH_TIME_OF_DAY = 0.5f;

    // How long the caller should wait after /bench before reading anything back. Reported in
    // the /bench response rather than enforced here - the server cannot make anyone wait, and
    // a number in the response is what stops the caller guessing.
    private static final int BENCH_WARMUP_SECONDS = 10;

    private final int port;
    private final AtomicReference<byte[]> latestPng = new AtomicReference<>();
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "frame-encoder");
        t.setDaemon(true);
        return t;
    });

    private HttpServer server;
    private long lastFrameAt;

    /*
     * When a viewer last asked for /frame.png. Capture stops entirely once nobody has asked
     * inside VIEWER_IDLE_TIMEOUT_MS.
     *
     * Without this the game reads back and encodes the framebuffer forever, whether or not a
     * browser exists — and that is not a small background cost. Measured while flying at render
     * distance 32: glReadPixels is a synchronous stall that drains the pipeline, encodePng
     * allocates a width x height int[] on top of the direct ByteBuffer and the byte[] copy, and
     * JFR put encodePng among the top allocation sites in the process with 5,621 GC phases in
     * 20 seconds. The GC that follows lands on the main thread, which is where the frame is.
     *
     * volatile because the HTTP threads write it and the render thread reads it.
     */
    private volatile long lastViewerPollAt;

    // Whether an encode is still in flight. Without it the render thread hands the worker a
    // frame every MIN_FRAME_INTERVAL_MS whether or not the last one finished, and a PNG encode
    // of a full-size window measures 60-220 ms against that 100 ms cadence — so the executor's
    // unbounded queue grows by a few frames a second, each holding a 4 MB copy. That reaches an
    // OutOfMemoryError in minutes, and because the encode task catches IOException only, the
    // error kills the task silently and latestPng stops updating: the stream freezes on a stale
    // frame long before the JVM dies. Skipping while busy is what makes the encode's cost stop
    // mattering — the stream just runs at whatever rate encoding sustains.
    private final AtomicBoolean encoding = new AtomicBoolean();

    // Set by the container so the command handlers can reach game state without this class
    // knowing what a Player is.
    private Consumer<float[]> teleportHandler = pose -> {};
    private Consumer<Float> timeHandler = t -> {};

    // Returns the /stats body, or null while nothing is wired. A Supplier rather than a
    // FrameLog: platform/debug has no business holding a util collector's lifetime, and the
    // two handlers above already answer how this class reaches game state.
    private Supplier<String> statsHandler = () -> null;

    // Clears whatever /stats reads, so a bench run starts from an empty log. Runs on the main
    // thread with the rest of the queued command, not on the HTTP thread that asked for it.
    private Runnable resetHandler = () -> {};

    public FrameStreamServer(int port) {
        this.port = port;
    }

    public void setTeleportHandler(Consumer<float[]> handler) {
        teleportHandler = handler;
    }

    public void setTimeHandler(Consumer<Float> handler) {
        timeHandler = handler;
    }

    /** Supplies the {@code /stats} body; called on an HTTP thread, so it must be thread-safe. */
    public void setStatsHandler(Supplier<String> handler) {
        statsHandler = handler;
    }

    public void setResetHandler(Runnable handler) {
        resetHandler = handler;
    }

    /** Binds 127.0.0.1 on the configured port and starts serving. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::serveViewer);
        server.createContext("/frame.png", this::serveFrame);
        server.createContext("/tp", this::acceptTeleport);
        server.createContext("/time", this::acceptTime);
        server.createContext("/bench", this::acceptBench);
        server.createContext("/stats", this::serveStats);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "frame-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        RENDER.info("frame stream on http://127.0.0.1:{}/", port);
    }

    public void stop() {
        if (server != null) server.stop(0);
        encoder.shutdownNow();
    }

    /**
     * Whether to capture this frame: a viewer is watching, the frame interval has passed, and the
     * previous encode has finished. Call before {@code readPixels}, so a declined frame costs
     * nothing; each {@code readPixels} stalls the pipeline and allocates a direct buffer that only
     * the GC frees.
     */
    public boolean wantsFrame(long nowMillis) {
        // Nobody is watching, so there is nothing to produce. Checked first because it is the
        // common case: the server is usually up for /stats and /tp while no browser is open.
        if (nowMillis - lastViewerPollAt > VIEWER_IDLE_TIMEOUT_MS) return false;
        if (nowMillis - lastFrameAt < MIN_FRAME_INTERVAL_MS) return false;
        if (encoding.get()) return false;
        lastFrameAt = nowMillis;
        return true;
    }

    /**
     * Copies RGB bytes from {@code glReadPixels} and encodes them off-thread. The caller may reuse
     * {@code rgb} as soon as this returns; its position is rewound.
     *
     * <p>At most one frame is outstanding: a submit during an encode is dropped. A viewer wants the
     * newest frame, and a backlog of old ones only costs memory.
     */
    public void submit(ByteBuffer rgb, int width, int height) {
        if (!encoding.compareAndSet(false, true)) return;
        byte[] copy = new byte[width * height * CHANNELS];
        rgb.get(copy);
        rgb.rewind();
        encoder.execute(() -> {
            try {
                latestPng.set(encodePng(copy, width, height));
            } catch (IOException e) {
                RENDER.warn("frame encode failed", e);
            } catch (Throwable t) {
                // Catching Throwable because the flag has to be cleared even for an Error. An
                // OutOfMemoryError here used to kill the task on the way out and leave nothing
                // to say so; letting it escape now would also wedge `encoding` set forever and
                // stop the stream permanently.
                RENDER.warn("frame encode failed hard", t);
            } finally {
                encoding.set(false);
            }
        });
    }

    /** Runs every queued command on the calling thread, which must be the main thread. */
    public void drainCommands() {
        Runnable next;
        while ((next = commands.poll()) != null) next.run();
    }

    /**
     * Encodes bottom-row-first RGB as PNG, reading rows back to front because GL's origin is
     * bottom-left and PNG's is top-left. {@code ScreenCapture} has stb do the same flip.
     */
    private static byte[] encodePng(byte[] rgb, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int srcRow = (height - 1 - y) * width * CHANNELS;
            for (int x = 0; x < width; x++) {
                int i = srcRow + x * CHANNELS;
                int r = rgb[i] & 0xFF;
                int g = rgb[i + 1] & 0xFF;
                int b = rgb[i + 2] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void serveFrame(HttpExchange exchange) throws IOException {
        // Before the null check, so the first request primes capture rather than being refused
        // forever: it 503s once, capture starts, and the viewer's next poll finds a frame.
        lastViewerPollAt = System.currentTimeMillis();

        byte[] png = latestPng.get();
        if (png == null) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, png.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(png);
        }
    }

    /**
     * Serves the debug page for exactly {@code /} and 404s the rest. {@code /} is the catch-all
     * context, so it also receives {@code /favicon.ico} and every mistyped path.
     */
    private void serveViewer(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] page;
        try (var in = getClass().getResourceAsStream("/debug/index.html")) {
            // A missing resource means the page never reached build/resources - a stale build
            // directory, usually. Falling back to the built-in viewer keeps the tool usable;
            // dereferencing the null would close the exchange with nothing said about why.
            if (in == null) {
                RENDER.warn("/debug/index.html not on the classpath - serving the built-in viewer");
                page = VIEWER_HTML.getBytes(StandardCharsets.UTF_8);
            } else {
                page = in.readAllBytes();
            }
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        // Re-read on every refresh, so editing the page only costs a processResources.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(page);
        }
    }

    /**
     * {@code /tp?x=&y=&z=&yaw=&pitch=}, any subset. An omitted or unparseable field travels as
     * {@code NaN}, which the handler reads as "keep the current value", distinct from zero.
     */
    private void acceptTeleport(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange);
        float[] pose = {
            parse(q.get("x")), parse(q.get("y")), parse(q.get("z")), parse(q.get("yaw")), parse(q.get("pitch"))
        };
        commands.add(() -> teleportHandler.accept(pose));
        respondOk(exchange);
    }

    /** {@code /time?t=0.5}: 0 is midnight and 0.5 noon, matching {@code DayNightCycle}. */
    private void acceptTime(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange);
        float t = parse(q.get("t"));
        if (!Float.isNaN(t)) commands.add(() -> timeHandler.accept(t));
        respondOk(exchange);
    }

    /**
     * {@code /bench}: teleports to the fixed benchmark pose, sets midday and clears the frame log,
     * then replies with the pose and the warm-up to wait before reading {@code /stats}.
     *
     * <p>It takes no parameters, because its value is that two runs are the same run. A pose that
     * can be overridden per call will be, and the numbers stop matching. Ad-hoc posing is {@code
     * /tp}'s job.
     */
    private void acceptBench(HttpExchange exchange) throws IOException {
        // The reset rides in the queued command rather than running here because whatever it
        // clears is written on the main thread. Draining it there is what keeps that object
        // single-threaded - clearing it from this HTTP thread would be the first data race.
        commands.add(() -> {
            teleportHandler.accept(BENCH_POSE);
            timeHandler.accept(BENCH_TIME_OF_DAY);
            resetHandler.run();
        });
        byte[] body = "bench pose x=%.1f y=%.1f z=%.1f yaw=%.1f pitch=%.1f time=%.2f warmup=%ds"
                .formatted(
                        BENCH_POSE[0],
                        BENCH_POSE[1],
                        BENCH_POSE[2],
                        BENCH_POSE[3],
                        BENCH_POSE[4],
                        BENCH_TIME_OF_DAY,
                        BENCH_WARMUP_SECONDS)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /**
     * {@code /stats}: the stats supplier's text, verbatim. Null or blank becomes a 501, which the
     * debug page shows as an unwired panel, distinct from the game being down.
     */
    private void serveStats(HttpExchange exchange) throws IOException {
        String stats = statsHandler.get();
        boolean wired = stats != null && !stats.isBlank();
        byte[] body = (wired ? stats : "stats not implemented").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(wired ? 200 : 501, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void respondOk(HttpExchange exchange) throws IOException {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static float parse(String value) {
        if (value == null) return Float.NaN;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static Map<String, String> parseQuery(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getQuery();
        if (raw == null || raw.isBlank()) return Map.of();
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) out.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return out;
    }

    private static final String VIEWER_HTML = """
            <!doctype html>
            <meta charset="utf-8">
            <title>Minecraft frame stream</title>
            <style>
              body { margin:0; background:#111; color:#ccc; font:13px/1.4 system-ui, sans-serif; }
              img  { display:block; width:100vw; height:auto; image-rendering:pixelated; }
              #bar { position:fixed; top:0; left:0; padding:6px 10px; background:rgba(0,0,0,.6); }
            </style>
            <div id="bar"><span id="fps">…</span> · /tp?x=&y=&z=&yaw=&pitch= · /time?t=0..1</div>
            <img id="v" alt="frame">
            <script>
              // Poll rather than stream: the server holds one PNG at a time, and a fresh GET is
              // simpler than MJPEG framing for something only a debugger looks at.
              const img = document.getElementById('v');
              const fps = document.getElementById('fps');
              let n = 0, t0 = performance.now();
              async function tick() {
                try {
                  const r = await fetch('/frame.png?' + Date.now(), { cache: 'no-store' });
                  if (r.ok) {
                    const blob = await r.blob();
                    const url = URL.createObjectURL(blob);
                    const old = img.src;
                    img.src = url;
                    if (old.startsWith('blob:')) URL.revokeObjectURL(old);
                    n++;
                  }
                } catch (e) { /* game not up yet */ }
                const dt = performance.now() - t0;
                if (dt > 1000) { fps.textContent = (n * 1000 / dt).toFixed(1) + ' fps'; n = 0; t0 = performance.now(); }
                setTimeout(tick, 80);
              }
              tick();
            </script>
            """;
}
