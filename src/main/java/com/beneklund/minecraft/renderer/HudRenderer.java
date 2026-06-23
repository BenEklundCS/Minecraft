package com.beneklund.minecraft.renderer;

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
                new DrawCall(highlightedSlot, ortho, HUD_COLOR, RenderPass.HUD),
                new DrawCall(hotBar, ortho, HUD_TEXTURE, RenderPass.HUD),
                new DrawCall(crosshair, ortho, HUD_COLOR, RenderPass.HUD));
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

            float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
            // TODO: Remove this magic number fix for color
            if (block == Block.OAK_LEAF) {
                r = 0.376f;
                g = 0.502f;
                b = 0.224f;
                a = 1.0f;
            }

            if (block == Block.GRASS) {
                r = 0.306f;
                g = 0.545f;
                b = 0.239f;
                a = 1.0f;
            }

            float[] slot = {
                x, y, r, g, b, a, uMin, vMax, // TL
                x + S, y, r, g, b, a, uMax, vMax, // TR
                x, y + S, r, g, b, a, uMin, vMin, // BL
                x + S, y + S, r, g, b, a, uMax, vMin, // BR
            };
            System.arraycopy(slot, 0, vertices, vBase, 32);

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

        float r = 0.5f, g = 0.5f, b = 0.5f, a = 1.0f, u = 0.0f, v = 0.0f;

        return new float[] {
            x, y, r, g, b, a, u, v, // TL
            x + S, y, r, g, b, a, u, v, // TR
            x, y + S, r, g, b, a, u, v, // BL
            x + S, y + S, r, g, b, a, u, v, // BR
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
        float r = 1f, g = 1f, b = 1f, a = 1f, u = 0f, v = 0f;
        float[] vertices = {
            // horizontal bar
            cx - half,
            cy - thick,
            r,
            g,
            b,
            a,
            u,
            v, // TL
            cx + half,
            cy - thick,
            r,
            g,
            b,
            a,
            u,
            v, // TR
            cx - half,
            cy + thick,
            r,
            g,
            b,
            a,
            u,
            v, // BL
            cx + half,
            cy + thick,
            r,
            g,
            b,
            a,
            u,
            v, // BR
            // vertical bar
            cx - thick,
            cy - half,
            r,
            g,
            b,
            a,
            u,
            v, // TL
            cx + thick,
            cy - half,
            r,
            g,
            b,
            a,
            u,
            v, // TR
            cx - thick,
            cy + half,
            r,
            g,
            b,
            a,
            u,
            v, // BL
            cx + thick,
            cy + half,
            r,
            g,
            b,
            a,
            u,
            v, // BR
        };
        int[] indices = {0, 1, 2, 1, 3, 2, 4, 5, 6, 5, 7, 6};
        crosshair = new HudMesh();
        crosshair.upload(vertices, indices);
    }
}
