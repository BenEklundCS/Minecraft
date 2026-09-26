package com.beneklund.minecraft.platform.audio;

/** Decodes an audio file from the classpath into PCM. */
public interface IAudioLoader {
    AudioData load(String classpathOgg);
}
