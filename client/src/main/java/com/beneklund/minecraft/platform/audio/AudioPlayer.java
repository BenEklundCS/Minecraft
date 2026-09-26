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

/**
 * Plays one looping track through OpenAL, owning the device and context.
 *
 * <p>OpenAL splits audio into buffers, which hold PCM samples, and sources, which hold playback
 * state such as position, gain and looping; one buffer can feed many sources. {@link #play} decodes
 * a file through the {@link IAudioLoader}, copies the PCM into a new buffer with {@code
 * alBufferData}, frees the decoded copy, and plays it from a single looping source. A second
 * {@link #play} replaces the first track.
 *
 * <p>The device opens on the first {@link #play}, so code that constructs a player and never plays,
 * such as a test, never needs an audio device. {@link #shutdown()} does nothing if it never
 * opened.
 *
 * @see <a href="https://www.openal.org/documentation/OpenAL_Programmers_Guide.pdf">OpenAL 1.1
 *     Programmer's Guide</a>
 * @see <a href="https://github.com/LWJGL/lwjgl3-wiki/wiki/2.1.-OpenAL">LWJGL wiki: OpenAL</a>
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

        ALCCapabilities alcCaps = ALC.createCapabilities(device);
        ALCapabilities alCaps = AL.createCapabilities(alcCaps);
        AUDIO.info("OpenAL device opened: {}", alcGetString(device, ALC_DEVICE_SPECIFIER));
        AUDIO.debug("AL10={} ALC11={}", alCaps.OpenAL10, alcCaps.OpenALC11);
    }

    /** Stops any current track and loops {@code classpathOgg}, opening the device on first use. */
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

    /** Stops and deletes the previous track's source and buffer, so repeated plays don't leak. */
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
