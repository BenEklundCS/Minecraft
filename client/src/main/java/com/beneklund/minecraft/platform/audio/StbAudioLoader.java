package com.beneklund.minecraft.platform.audio;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.lwjgl.stb.STBVorbisInfo;

/**
 * Decodes Ogg Vorbis files from the classpath into interleaved 16-bit PCM with stb_vorbis, the
 * whole file at once.
 *
 * <p>The PCM buffer lives in native memory and is freed when the returned {@link AudioData}
 * closes. {@code stb_vorbis_stream_length_in_samples} seeks to the end of the stream to count, so
 * the decoder seeks back to the start before decoding. Native writes leave the Java buffer's
 * position at 0 and its limit at capacity, which is exactly the range OpenAL should read, so the
 * buffer goes to {@code alBufferData} without a {@code flip()}.
 *
 * @see <a href="https://github.com/nothings/stb/blob/master/stb_vorbis.c">stb_vorbis.c</a>
 */
public class StbAudioLoader implements IAudioLoader {
    private static final String RESOURCE_ROOT = "/";
    private static final String OGG_SUFFIX = ".ogg";

    /**
     * Decodes {@code classpathOgg}, resolved from the classpath root with or without a leading
     * slash. {@code Class.getResourceAsStream} reads a slash-less name relative to this class's
     * package, so the path is normalised first and {@code music/album/track.ogg} from a config
     * file works as written.
     */
    @Override
    public AudioData load(String classpathOgg) {
        String path = getPath(classpathOgg);
        try (var is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Resource not found: %s".formatted(path));
            byte[] bytes = is.readAllBytes();
            ByteBuffer oggBytes = memAlloc(bytes.length);
            oggBytes.put(bytes).flip();

            try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                int[] error = {0};
                long decoder = stb_vorbis_open_memory(oggBytes, error, null);
                if (decoder == NULL) throw new RuntimeException("Failed to decode OGG (error %s)".formatted(error[0]));

                stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();

                int sampleCount = stb_vorbis_stream_length_in_samples(decoder);
                stb_vorbis_seek_start(decoder);
                ShortBuffer pcm = memAllocShort(sampleCount * channels);
                stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                stb_vorbis_close(decoder);
                memFree(oggBytes);

                return new AudioData(pcm, channels, sampleRate, () -> memFree(pcm));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load audio: %s".formatted(classpathOgg), e);
        }
    }

    private String getPath(String classpathOgg) {
        return classpathOgg.startsWith(RESOURCE_ROOT) ? classpathOgg : RESOURCE_ROOT + classpathOgg;
    }

    /**
     * Every {@code .ogg} at or below a classpath directory, as paths {@link #load} accepts, sorted
     * so a seeded pick is reproducible across filesystems.
     *
     * <p>Recursive, so installing an album is dropping its folder in; no track is named in code.
     * Works from a directory or from inside a jar, which is how {@code :launcher} puts the client
     * on the classpath. Returns an empty list when {@code dir} is absent, because the music folder
     * is gitignored and missing on a fresh clone.
     *
     * @param dir classloader-relative, no leading slash, e.g. {@code music}
     */
    public List<String> listOggs(String dir) {
        URL url = getContextClassLoader().getResource(dir);
        if (url == null) return List.of();

        try {
            URI uri = url.toURI();
            // A jar when run through :launcher, which puts client on the classpath as a jar.
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    return oggsUnder(jar.provider().getPath(uri), dir);
                }
            }
            if (!"file".equals(uri.getScheme())) return List.of();
            return oggsUnder(Paths.get(uri), dir);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to list audio resources under %s".formatted(dir), e);
        }
    }

    private static List<String> oggsUnder(Path root, String dir) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(OGG_SUFFIX))
                    // Always '/', never '\' — a classpath resource name is not a Windows path.
                    .map(p ->
                            "%s/%s".formatted(dir, root.relativize(p).toString().replace('\\', '/')))
                    .sorted()
                    .toList();
        }
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
