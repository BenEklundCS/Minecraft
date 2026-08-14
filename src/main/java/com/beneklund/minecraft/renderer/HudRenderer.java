package com.beneklund.minecraft.renderer;

import com.beneklund.minecraft.block.Block;
import com.beneklund.minecraft.block.BlockDef;
import com.beneklund.minecraft.block.BlockRegistry;
import com.beneklund.minecraft.platform.graphics.Geometry;
import com.beneklund.minecraft.platform.graphics.HudMesh;
import com.beneklund.minecraft.util.Direction;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private Vector2f lastWindowSize = new Vector2f();
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
        rebuildHotBar();
        rebuildCrosshair();
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

        return List.of(
                new DrawCall(highlightedSlot, ortho, HUD_COLOR, Optional.empty(), RenderPass.HUD, Map.of()),
                new DrawCall(hotBar, ortho, HUD_TEXTURE, atlas, RenderPass.HUD),
                new DrawCall(crosshair, ortho, HUD_COLOR, Optional.empty(), RenderPass.HUD, Map.of()));
    }

    // Clearing lastWindowSize is not optional. Uniform values live on the GL program object,
    // and a reload builds a brand new one where every uniform starts at its default — for a
    // mat4 that's all zeros. uOrtho is the only uniform in the codebase written once and left
    // there (getDrawCalls only sets it when the window size changes), so after a reload every
    // HUD vertex gets multiplied by a zero matrix and collapses to the origin. Nulling this
    // makes the next getDrawCalls take the resize branch and re-upload it.
    @Override
    public void reload() {
        HUD_COLOR.reload();
        HUD_TEXTURE.reload();
        lastWindowSize = null;
    }

    private void rebuildHotBar() {
        if (hotBar != null) {
            hotBar.delete();
            hotBar = null;
        }
        hotBar = new HudMesh(getHotbarGeometry());
    }

    private void rebuildHighlightedSlot() {
        if (highlightedSlot != null) {
            highlightedSlot.delete();
            highlightedSlot = null;
        }
        highlightedSlot = new HudMesh(getHighlightedSlotGeometry());
    }

    private void rebuildCrosshair() {
        if (crosshair != null) {
            crosshair.delete();
            crosshair = null;
        }
        crosshair = new HudMesh(getCrosshairGeometry());
    }

    private Geometry getHotbarGeometry() {
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
        return new Geometry(vertices, indices);
    }

    private Geometry getHighlightedSlotGeometry() {
        float totalWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
        float startX = (lastWindowSize.x - totalWidth) / 2f;
        float startY = lastWindowSize.y - SLOT_SIZE - SLOT_MARGIN;

        float x = startX + selectedSlot * (SLOT_SIZE + SLOT_GAP) - 2f;
        float y = startY - 2f;
        float S = SLOT_SIZE + 4f; // 2px bleed on each side

        float r = 0.5f, g = 0.5f, b = 0.5f, a = 1.0f, u = 0.0f, v = 0.0f;

        float[] vertices = {
            x, y, r, g, b, a, u, v, // TL
            x + S, y, r, g, b, a, u, v, // TR
            x, y + S, r, g, b, a, u, v, // BL
            x + S, y + S, r, g, b, a, u, v, // BR
        };
        int[] indices = new int[] {0, 1, 2, 1, 3, 2};

        return new Geometry(vertices, indices);
    }

    private Geometry getCrosshairGeometry() {
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
        int[] indices = new int[] {0, 1, 2, 1, 3, 2, 4, 5, 6, 5, 7, 6};
        return new Geometry(vertices, indices);
    }
}
