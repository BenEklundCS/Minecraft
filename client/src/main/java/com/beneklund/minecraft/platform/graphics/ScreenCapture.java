package com.beneklund.minecraft.platform.graphics;

import static org.lwjgl.opengl.GL11C.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;

/** Reads the bound framebuffer back to the CPU and writes it out as a PNG. */
public class ScreenCapture {
    public static String SCREENSHOT_DIR = "screenshots";
    public static final int CHANNELS = 3;

    /**
     * Reads the bound read framebuffer into a tightly packed RGB8 buffer, bottom row first.
     *
     * <p>{@code glReadPixels} is synchronous: it waits for every queued draw to finish before
     * copying, so each call stalls the frame. Pack alignment is set to 1 because the default of 4
     * pads each row, and a 3-byte-per-pixel row is only 4-aligned when the width is a multiple of
     * 4.
     *
     * @see <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glReadPixels.xhtml">
     *     glReadPixels</a>
     */
    public static ByteBuffer readPixels(int width, int height) {
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * CHANNELS);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buf);
        return buf;
    }

    /**
     * Writes pixels from {@link #readPixels} to a timestamped PNG in {@code dir}, flipped so the
     * image's top row comes first as PNG expects.
     *
     * @return the file written
     */
    public static Path write(ByteBuffer pixels, int width, int height, Path dir) throws IOException {
        Files.createDirectories(dir);
        String name = "%s%s"
                .formatted(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now()), ".png");
        Path out = dir.resolve(name);
        STBImageWrite.stbi_flip_vertically_on_write(true);
        STBImageWrite.stbi_write_png(out.toString(), width, height, CHANNELS, pixels, width * CHANNELS);
        return out;
    }
}
