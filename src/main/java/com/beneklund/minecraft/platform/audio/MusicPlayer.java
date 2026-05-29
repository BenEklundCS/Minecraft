package com.beneklund.minecraft.platform.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.stb.STBVorbisInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;

public class MusicPlayer {
    private long device;
    private long context;
    private int source;
    private int buffer;

    private void init() {
        this.device = alcOpenDevice((String) null);
        if (this.device == NULL) throw new RuntimeException("Failed to open OpenAL device");

        this.context = alcCreateContext(this.device, new int[]{0});
        alcMakeContextCurrent(this.context);

        ALCCapabilities alcCaps = ALC.createCapabilities(this.device);
        ALCapabilities alCaps = AL.createCapabilities(alcCaps);
    }

    public void play(String classpathOgg) {
        if (this.device == NULL) {
            init();
        }

        ByteBuffer oggBytes = loadResource(classpathOgg);

        try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
            int[] error = {0};
            long decoder = stb_vorbis_open_memory(oggBytes, error, null);
            if (decoder == NULL) throw new RuntimeException("Failed to decode OGG (error " + error[0] + ")");

            stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();

            ShortBuffer pcm = BufferUtils.createShortBuffer(stb_vorbis_stream_length_in_samples(decoder) * channels);
            stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
            stb_vorbis_close(decoder);
            memFree(oggBytes);

            int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            this.buffer = alGenBuffers();
            alBufferData(this.buffer, format, pcm, sampleRate);

            this.source = alGenSources();
            alSourcei(this.source, AL_BUFFER, this.buffer);
            alSourcei(this.source, AL_LOOPING, AL_TRUE);
            alSourcePlay(this.source);
        }
    }

    public void shutdown() {
        alSourceStop(this.source);
        alDeleteSources(this.source);
        alDeleteBuffers(this.buffer);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(this.context);
        alcCloseDevice(this.device);
    }

    private ByteBuffer loadResource(String classpathOgg) {
        try (var is = MusicPlayer.class.getClassLoader().getResourceAsStream(classpathOgg)) {
            if (is == null) throw new RuntimeException("Resource not found: " + classpathOgg);
            byte[] bytes = is.readAllBytes();
            ByteBuffer buf = memAlloc(bytes.length);
            buf.put(bytes).flip();
            return buf;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load audio: " + classpathOgg, e);
        }
    }
}
