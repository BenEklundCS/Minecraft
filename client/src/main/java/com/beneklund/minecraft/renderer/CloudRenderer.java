package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glDisable;

import com.beneklund.minecraft.platform.graphics.GlFramebuffer;
import com.beneklund.minecraft.platform.graphics.SkyMesh;
import com.beneklund.minecraft.platform.graphics.UniformValue;
import java.util.Map;

/**
 * Raymarches the cloud volume into its own reduced-resolution buffer before the scene is drawn.
 * {@code sky.frag} samples that buffer through {@code uCloudBuffer} and composites it into the sky.
 *
 * <p>Owns its render target because the march is too expensive at full resolution, and a {@link
 * DrawCall} can't pick a target. {@code GameContainer} sizes the buffer at a third of the window
 * per axis. Follows the shadow pass pattern: render to a buffer, then bind it as a texture in a
 * later shader. The clouds are scene input to the tonemap.
 *
 * <p>Shares {@code sky.vert} with {@link SkyRenderer}: one fullscreen triangle, one world-space
 * view ray per pixel.
 *
 * @see <a href="https://www.guerrilla-games.com/read/the-real-time-volumetric-cloudscapes-of-horizon-zero-dawn">
 *     Schneider: The Real-time Volumetric Cloudscapes of Horizon Zero Dawn</a>
 */
public class CloudRenderer implements IGpuResource {
    private static final String VERT_PATH = "/shaders/sky.vert";
    private static final String FRAG_PATH = "/shaders/cloud.frag";

    private final ShaderProgram shader;
    private final SkyMesh mesh;

    public CloudRenderer() {
        shader = new ShaderProgram(VERT_PATH, FRAG_PATH);
        mesh = new SkyMesh();
    }

    /**
     * Marches the clouds into {@code target}, overwriting every pixel.
     *
     * <p>Skips {@code glClear} because {@code cloud.frag} writes every pixel the triangle covers,
     * cloudless ones included. Depth testing is off since one screen-covering triangle has nothing
     * to sort; the clouds land behind terrain because the sky pass that reads this buffer draws
     * first.
     *
     * @param frame the render loop's frame counter, for {@code GlShader.apply}'s once-per-frame
     *     guard. This pass draws once per frame, so the guard never trips here.
     */
    public void draw(GlFramebuffer target, long frame, Map<String, UniformValue<?>> frameUniforms) {
        target.bind();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        shader.bind();
        shader.apply(frame, frameUniforms);
        mesh.render();
    }

    public void reload() {
        shader.reload();
    }

    public void delete() {
        shader.delete();
        mesh.delete();
    }
}
