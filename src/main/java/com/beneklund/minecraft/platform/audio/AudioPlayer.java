package com.beneklund.minecraft.platform.audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.ByteBuffer;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

/*
 * Owns the OpenAL device/context lifecycle and drives playback. Decoding is delegated
 * to AudioLoader (StbAudioLoader by default), which returns an AudioData holding decoded
 * PCM samples. This class uploads that PCM to an AL buffer, attaches it to a source, and plays.
 *
 * OpenAL separates buffers (raw PCM data) from sources (playback state: position, volume,
 * looping). One buffer can be shared across many sources without duplicating audio data.
 *
 * init() is lazy - deferred until the first play() call so the audio device isn't opened
 * if no music is configured (e.g. in headless test environments).
 *
 * Lifecycle: play() -> shutdown() on app exit. shutdown() is a no-op if play() was never called.
 */
public class AudioPlayer {
    private final AudioLoader loader;
    private long device;
    private long context;
    private int source;
    private int buffer;

    public AudioPlayer() {
        this.loader = new StbAudioLoader();
    }

    private void init() {
        // null = let OpenAL pick the default audio device.
        this.device = alcOpenDevice((ByteBuffer) null);
        if (this.device == NULL) throw new RuntimeException("Failed to open OpenAL device");

        this.context = alcCreateContext(this.device, new int[] {0});
        alcMakeContextCurrent(this.context);

        // Reads driver support and makes the AL functions callable.
        ALCCapabilities alcCaps = ALC.createCapabilities(this.device);
        ALCapabilities alCaps = AL.createCapabilities(alcCaps);
    }

    public void play(String classpathOgg) {
        if (this.device == NULL) {
            init();
        }

        try (AudioData data = this.loader.load(classpathOgg)) {
            int format = data.channels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            this.buffer = alGenBuffers();
            alBufferData(this.buffer, format, data.pcm(), data.sampleRate());

            this.source = alGenSources();
            alSourcei(this.source, AL_BUFFER, this.buffer);
            alSourcei(this.source, AL_LOOPING, AL_TRUE);
            alSourcePlay(this.source);
        }
    }

    public void shutdown() {
        if (this.device == NULL) return;
        alSourceStop(this.source);
        alDeleteSources(this.source);
        alDeleteBuffers(this.buffer);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(this.context);
        alcCloseDevice(this.device);
    }
}
