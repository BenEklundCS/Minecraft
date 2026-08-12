package com.beneklund.minecraft.platform.audio;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.lwjgl.stb.STBVorbisInfo;

/*
 * Decodes an OGG/Vorbis file from the classpath into raw PCM samples using STBVorbis.
 * Returns an AudioData holding a native ShortBuffer (interleaved 16-bit samples), channel
 * count, and sample rate. The caller is responsible for freeing via AudioData.close().
 *
 * stb_vorbis_stream_length_in_samples seeks to the end of the stream, so stb_vorbis_seek_start
 * is called before decoding to reset the read position.
 *
 * Native writes via STBVorbis do not advance the Java buffer position, so the ShortBuffer
 * is returned as-is (position=0, limit=capacity) - do not flip before passing to OpenAL.
 */
public class StbAudioLoader implements IAudioLoader {
    private static final String RESOURCE_ROOT = "/";
    private static final String OGG_SUFFIX = ".ogg";

    @Override
    public AudioData load(String classpathOgg) {
        // Resolve from the classpath root. getResourceAsStream treats a slash-less path as
        // relative to this class's package, so normalize to a leading slash - that way a
        // config value like "music/<album>/<track>.ogg" works the same as "/music/...".
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

    // Every .ogg at or below a classpath directory, as paths load() accepts. `dir` is
    // classloader-relative with no leading slash, e.g. "music". Recursive, so subfolders are
    // whatever is on disk — no track or album is named in code, and dropping a folder in is
    // the whole install step.
    //
    // Sorted, so the ordering doesn't depend on the filesystem and a seeded pick would be
    // reproducible.
    //
    // Empty rather than throwing when the directory isn't on the classpath: a gitignored music
    // folder is absent on a fresh clone and that is not an error. Whether finding nothing
    // *anywhere* matters is the caller's decision.
    public List<String> listOggs(String dir) {
        URL url = getContextClassLoader().getResource(dir);
        if (url == null) return List.of();

        // Only an exploded classpath directory can be walked as a filesystem. Inside a jar the
        // protocol is "jar" and this comes back empty — see DG-14 before shipping a build.
        if (!"file".equals(url.getProtocol())) return List.of();

        try {
            Path root = Paths.get(url.toURI());
            try (Stream<Path> tree = Files.walk(root)) {
                return tree.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(OGG_SUFFIX))
                        // Always '/', never '\' — a classpath resource name is not a Windows path.
                        .map(p -> "%s/%s"
                                .formatted(dir, root.relativize(p).toString().replace('\\', '/')))
                        .sorted()
                        .toList();
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to list audio resources under %s".formatted(dir), e);
        }
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
