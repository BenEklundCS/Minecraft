package com.beneklund.minecraft.renderer;

import static org.lwjgl.opengl.GL11.*;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.platform.graphics.HudMesh;
import com.beneklund.minecraft.util.Direction;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector2f;

public class HudRenderer implements IRenderable {
    private final BlockRegistry blocks;
    private final TextureAtlas atlas;

    private HudMesh hotBar;
    private HudMesh highlightedSlot;
    private HudMesh crosshair;

    private static final ShaderProgram HUD_COLOR = new ShaderProgram("/shaders/hud.vert", "/shaders/hud_color.frag");
    private static final ShaderProgram HUD_TEXTURE = new ShaderProgram("/shaders/hud.vert", "/shaders/hud.frag");
    private Vector2f lastWindowSize;
    private Matrix4f ortho = new Matrix4f();
    private boolean layoutDirty = false;

    private static final float SLOT_SIZE = 40f;
    private static final float SLOT_GAP = 2f;
    private static final float SLOT_MARGIN = 8f;
    private static final int SLOT_COUNT = 9;

    private Block[] hotbarSlots = new Block[SLOT_COUNT];
    private int selectedSlot = 0;

    public HudRenderer(BlockRegistry blocks, TextureAtlas atlas) {
        this.blocks = blocks;
        this.atlas = atlas;
        hotBar = new HudMesh();
        crosshair = new HudMesh();
    }

    public void setHotbar(Block[] slots, int selected) {
        if (!Arrays.equals(hotbarSlots, slots) || selectedSlot != selected) {
            hotbarSlots = slots.clone();
            selectedSlot = selected;
            layoutDirty = true;
        }
    }

    @Override
    public List<DrawCall> getDrawCalls(Camera camera) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Vector2f windowSize = camera.getWindowSize();
        if (!windowSize.equals(lastWindowSize)) {
            lastWindowSize = new Vector2f(windowSize);
            ortho = new Matrix4f().ortho(0, windowSize.x, windowSize.y, 0, -1f, 1f);
            HUD_COLOR.bind();
            HUD_COLOR.setUniformMat4("uOrtho", ortho);
            HUD_TEXTURE.bind();
            HUD_TEXTURE.setUniformMat4("uOrtho", ortho);
            layoutDirty = true;
        }

        if (layoutDirty) {
            rebuildCrosshair();
            rebuildHotBar();
            rebuildHighlightedSlot();
            layoutDirty = false;
        }

        atlas.bind();
        return List.of(
                new DrawCall(highlightedSlot, ortho, HUD_COLOR),
                new DrawCall(hotBar, ortho, HUD_TEXTURE),
                new DrawCall(crosshair, ortho, HUD_COLOR));
    }

    private void rebuildHotBar() {
        if (hotBar != null) {
            hotBar.delete();
            hotBar = null;
        }

        float totalWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
        float startX = (lastWindowSize.x - totalWidth) / 2f;
        float startY = lastWindowSize.y - SLOT_SIZE - SLOT_MARGIN;

        float[] vertices = new float[SLOT_COUNT * 4 * 8];
        int[] indices = new int[SLOT_COUNT * 6];
        int count = 0;

        for (int i = 0; i < SLOT_COUNT; i++) {
            float x = startX + i * (SLOT_SIZE + SLOT_GAP);
            float y = startY;

            Block block = hotbarSlots[i];
            BlockDef def = block != null ? blocks.get(block) : null;

            float uMin = 0f, vMin = 0f, uMax = 0f, vMax = 0f;
            if (def != null) {
                float[] uvs = atlas.getFaceUVs(def, Direction.UP);
                uMin = uvs[0];
                vMin = uvs[1];
                uMax = uvs[2];
                vMax = uvs[3];
            }

            int vBase = count * 4 * 8;
            float S = SLOT_SIZE;

            float r = 1f, g = 1f, b = 1f, a = 1f;
            // TODO: Remove this magic number fix for color
            if (block == Block.OAK_LEAF) {
                r = 0.376f;
                g = 0.502f;
                b = 0.224f;
                a = 1f;
            }

            // TL
            vertices[vBase] = x;
            vertices[vBase + 1] = y;
            vertices[vBase + 2] = r;
            vertices[vBase + 3] = g;
            vertices[vBase + 4] = b;
            vertices[vBase + 5] = a;
            vertices[vBase + 6] = uMin;
            vertices[vBase + 7] = vMax;
            // TR
            vertices[vBase + 8] = x + S;
            vertices[vBase + 9] = y;
            vertices[vBase + 10] = r;
            vertices[vBase + 11] = g;
            vertices[vBase + 12] = b;
            vertices[vBase + 13] = a;
            vertices[vBase + 14] = uMax;
            vertices[vBase + 15] = vMax;
            // BL
            vertices[vBase + 16] = x;
            vertices[vBase + 17] = y + S;
            vertices[vBase + 18] = r;
            vertices[vBase + 19] = g;
            vertices[vBase + 20] = b;
            vertices[vBase + 21] = a;
            vertices[vBase + 22] = uMin;
            vertices[vBase + 23] = vMin;
            // BR
            vertices[vBase + 24] = x + S;
            vertices[vBase + 25] = y + S;
            vertices[vBase + 26] = r;
            vertices[vBase + 27] = g;
            vertices[vBase + 28] = b;
            vertices[vBase + 29] = a;
            vertices[vBase + 30] = uMax;
            vertices[vBase + 31] = vMin;

            int iBase = count * 6;
            int v = count * 4;
            indices[iBase] = v;
            indices[iBase + 1] = v + 1;
            indices[iBase + 2] = v + 2;
            indices[iBase + 3] = v + 1;
            indices[iBase + 4] = v + 3;
            indices[iBase + 5] = v + 2;
            count++;
        }

        hotBar = new HudMesh();
        hotBar.upload(Arrays.copyOf(vertices, count * 4 * 8), Arrays.copyOf(indices, count * 6));
    }

    private void rebuildHighlightedSlot() {
        if (highlightedSlot != null) {
            highlightedSlot.delete();
            highlightedSlot = null;
        }

        float totalWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
        float startX = (lastWindowSize.x - totalWidth) / 2f;
        float[] vertices = getHighlightedSlotVertices(startX);
        int[] indices = {0, 1, 2, 1, 3, 2};

        highlightedSlot = new HudMesh();
        highlightedSlot.upload(vertices, indices);
    }

    private float[] getHighlightedSlotVertices(float startX) {
        float startY = lastWindowSize.y - SLOT_SIZE - SLOT_MARGIN;

        float x = startX + selectedSlot * (SLOT_SIZE + SLOT_GAP) - 2f;
        float y = startY - 2f;
        float S = SLOT_SIZE + 4f; // 2px bleed on each side

        return new float[] {
            x, y, 1f, 0.8f, 0f, 1f, 0f, 0f, // TL — yellow
            x + S, y, 1f, 0.8f, 0f, 1f, 0f, 0f, // TR
            x, y + S, 1f, 0.8f, 0f, 1f, 0f, 0f, // BL
            x + S, y + S, 1f, 0.8f, 0f, 1f, 0f, 0f, // BR
        };
    }

    private void rebuildCrosshair() {
        if (crosshair != null) {
            crosshair.delete();
            crosshair = null;
        }
        float cx = lastWindowSize.x / 2f, cy = lastWindowSize.y / 2f;
        float half = 8f;
        float thick = 1.5f;
        float[] vertices = {
            cx - half,
            cy - thick,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx + half,
            cy - thick,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx - half,
            cy + thick,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx + half,
            cy + thick,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx - thick,
            cy - half,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx + thick,
            cy - half,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx - thick,
            cy + half,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
            cx + thick,
            cy + half,
            1f,
            1f,
            1f,
            1f,
            0f,
            0f,
        };
        int[] indices = {0, 1, 2, 1, 3, 2, 4, 5, 6, 5, 7, 6};
        crosshair = new HudMesh();
        crosshair.upload(vertices, indices);
    }
}
