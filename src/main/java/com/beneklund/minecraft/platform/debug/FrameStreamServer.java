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

/*
 * Serves the last rendered frame over localhost, and accepts a few commands back.
 *
 * This exists because the only way to debug something like shadow flicker is to watch it while
 * changing one thing at a time, and screenshots pasted back and forth are too slow a loop to do
 * that. A browser pointed at this can watch the game live; the command endpoints make a run
 * reproducible, which is what turns "does that constant help" from a judgement call into a
 * comparison of two images taken from the same place at the same time of day.
 *
 * Off unless local.properties sets framestream.port. It binds the loopback address only.
 *
 * Threading: submit() is called from the render thread and does nothing but copy bytes — the
 * PNG encode happens on a single worker so a 1200x800 encode never lands in the frame budget.
 * Commands arrive on HTTP threads and are queued; drainCommands() runs them on the main thread,
 * because they touch the player and the day cycle and nothing else may.
 */
public class FrameStreamServer {

    // The browser polls faster than this; the limit is here so the encode worker and the render
    // thread's readPixels stay off the frame budget. 10 fps is plenty to see flicker.
    private static final long MIN_FRAME_INTERVAL_MS = 100;
    private static final int CHANNELS = 3;

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

    public FrameStreamServer(int port) {
        this.port = port;
    }

    public void setTeleportHandler(Consumer<float[]> handler) {
        teleportHandler = handler;
    }

    public void setTimeHandler(Consumer<Float> handler) {
        timeHandler = handler;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::serveViewer);
        server.createContext("/frame.png", this::serveFrame);
        server.createContext("/tp", this::acceptTeleport);
        server.createContext("/time", this::acceptTime);
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

    // True if enough time has passed that another frame is worth capturing, and the last one has
    // finished encoding. Checked before readPixels so a declined frame costs nothing at all —
    // which matters twice over, because readPixels allocates a fresh direct ByteBuffer per call
    // and those are freed only when the GC gets round to them.
    public boolean wantsFrame(long nowMillis) {
        if (nowMillis - lastFrameAt < MIN_FRAME_INTERVAL_MS) return false;
        if (encoding.get()) return false;
        lastFrameAt = nowMillis;
        return true;
    }

    /*
     * Takes raw RGB bytes straight from glReadPixels. Copies them immediately — the caller may
     * reuse or discard its buffer — and encodes off-thread.
     *
     * Never queues behind an encode already running: wantsFrame() declines while `encoding` is
     * set, so at most one frame is ever outstanding. Dropping frames is the right trade for an
     * instrument — a viewer wants the newest frame, never a backlog of old ones.
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

    // Runs queued commands on the calling thread. Call from the main loop.
    public void drainCommands() {
        Runnable next;
        while ((next = commands.poll()) != null) next.run();
    }

    // GL's origin is bottom-left and every image format's is top-left, so rows are read back
    // to front. Same flip ScreenCapture asks STB to do.
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

    private void serveViewer(HttpExchange exchange) throws IOException {
        byte[] page = VIEWER_HTML.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(page);
        }
    }

    // /tp?x=..&y=..&z=..&yaw=..&pitch=..  — any subset; omitted fields keep their current value,
    // signalled as NaN so the handler can tell "not supplied" from "zero".
    private void acceptTeleport(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange);
        float[] pose = {
            parse(q.get("x")), parse(q.get("y")), parse(q.get("z")), parse(q.get("yaw")), parse(q.get("pitch"))
        };
        commands.add(() -> teleportHandler.accept(pose));
        respondOk(exchange);
    }

    // /time?t=0.5 — 0 is midnight, 0.5 noon, matching DayNightCycle.
    private void acceptTime(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange);
        float t = parse(q.get("t"));
        if (!Float.isNaN(t)) commands.add(() -> timeHandler.accept(t));
        respondOk(exchange);
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
