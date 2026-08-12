package com.beneklund.minecraft.platform.audio;

import static com.beneklund.minecraft.util.Log.AUDIO;
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
    private final IAudioLoader loader;
    private long device;
    private long context;
    private int source;
    private int buffer;

    public AudioPlayer(IAudioLoader loader) {
        this.loader = loader;
    }

    private void init() {
        // null = let OpenAL pick the default audio device.
        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) throw new RuntimeException("Failed to open OpenAL device");

        context = alcCreateContext(device, new int[] {0});
        alcMakeContextCurrent(context);

        // Reads driver support and makes the AL functions callable.
        ALCCapabilities alcCaps = ALC.createCapabilities(device);
        ALCapabilities alCaps = AL.createCapabilities(alcCaps);
        AUDIO.info("OpenAL device opened: {}", alcGetString(device, ALC_DEVICE_SPECIFIER));
        AUDIO.debug("AL10={} ALC11={}", alCaps.OpenAL10, alcCaps.OpenALC11);
    }

    public void play(String classpathOgg) {
        if (device == NULL) {
            init();
        }
        clean();
        try (AudioData data = loader.load(classpathOgg)) {
            int format = data.channels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            buffer = alGenBuffers();
            alBufferData(buffer, format, data.pcm(), data.sampleRate());

            source = alGenSources();
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_LOOPING, AL_TRUE);
            alSourcePlay(source);
            AUDIO.debug("playing {} ({} ch, {} Hz, looping)", classpathOgg, data.channels(), data.sampleRate());
        }
    }

    public void shutdown() {
        if (device == NULL) return;
        AUDIO.debug("closing OpenAL device");
        alSourceStop(source);
        alDeleteSources(source);
        alDeleteBuffers(buffer);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    // Called before each play() to avoid leaking AL objects if play() is called multiple times.
    private void clean() {
        if (source != 0) {
            alSourceStop(source);
            alDeleteSources(source);
            alDeleteBuffers(buffer);
            source = 0;
            buffer = 0;
        }
    }

    private void _play(String classpathOgg) {}
}
