package com.beneklund.minecraft.platform.images;

/** Decodes an image from the classpath into native memory. */
public interface IImageLoader {
    ImageData load(String classpathPng);
}
