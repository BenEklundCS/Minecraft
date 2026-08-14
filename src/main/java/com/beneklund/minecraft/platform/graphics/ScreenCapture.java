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

public class ScreenCapture {
    public static String SCREENSHOT_DIR = "screenshots";
    public static final int CHANNELS = 3;

    public static ByteBuffer readPixels(int width, int height) {
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * CHANNELS);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buf);
        return buf;
    }

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
