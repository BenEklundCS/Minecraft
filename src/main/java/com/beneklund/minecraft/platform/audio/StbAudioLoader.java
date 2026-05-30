package com.beneklund.minecraft.platform.audio;

import org.lwjgl.stb.STBVorbisInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;


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
public class StbAudioLoader implements AudioLoader {

    @Override
    public AudioData load(String classpathOgg) {
        try (var is = getClass().getResourceAsStream(classpathOgg)) {
            if (is == null) throw new RuntimeException("Resource not found: %s".formatted(classpathOgg));
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
}
