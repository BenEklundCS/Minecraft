# Phase 18 — HUD: an OpenGL concept review

This is a learning guide, not a code drop. The HUD is a small, self-contained project that touches most of the "rest of OpenGL" you haven't used yet: a second render pass, orthographic projection, 2D textured quads, alpha blending, and draw ordering. Each section explains the concept from first principles, points you at something worth reading, and gives you the steps — you write the code.

---

## Where your code actually is right now

Before the concepts, an honest snapshot so the steps below line up with what's on disk:

- `hud.vert` currently passes a `vec3 aPos` straight through in **clip space** (`gl_Position = vec4(aPos, 1.0)`), with a `vec3 aColor`. There is **no projection matrix and no UV** yet.
- `hud.frag` is `FragColor = vec4(vColor, 1.0)` — solid color, no texture sampling, no alpha.
- `HudRenderer` builds a single hardcoded triangle out of `TriangleMesh` and currently returns `List.of()` (the draw call is commented out).
- `TriangleMesh` is **6 floats per vertex** — `x, y, z, r, g, b`, stride 24. It has **no texture coordinates**, so as-is it can draw the crosshair, hotbar background, and highlight (all solid colors) but **not** block icons.
- `Player` already exposes `getSelectedSlot()` (0-indexed). The hotbar itself is `Map<Integer, Block>`, **1-indexed** (slot key = `selectedSlot + 1`).

So the through-line of this phase: take the clip-space colored-triangle scaffold and grow it into a screen-pixel, optionally-textured 2D layer. The single biggest decision is **how UVs get into the HUD mesh** (section 3), because icons depend on it.

---

## The big idea: two projections, one pipeline

Your world is drawn with a **perspective** projection — distant things shrink, because the projection divides x/y by depth. A HUD wants the opposite: a pixel is a pixel regardless of where the camera looks. That's an **orthographic** projection — a box mapped to the screen with no depth divide. Critically, "perspective vs orthographic" is *just a different matrix*. Same shaders-compile-the-same-way, same VAO/VBO, same `glDrawElements`. Only the matrix and a little GL state change.

Read this first — it's the single most relevant page for the whole phase:
- LearnOpenGL, Coordinate Systems (perspective vs ortho, clip space, the matrix chain): https://learnopengl.com/Getting-started/Coordinate-Systems

How it fits your engine: `Renderer.draw()` loops every `IRenderable`, and for each `DrawCall` it binds the shader and sets `uView`, `uProjection`, `uModel` from the camera, then renders. The HUD is just another `IRenderable` registered **last**. Its shader won't use the camera's perspective `uProjection`/`uView` at all — it uses its **own** ortho uniform that `HudRenderer` sets itself. The camera uniforms still get written each frame; an unused uniform is harmless.

---

## 18.1 — The HUD shaders, and thinking in screen pixels

Right now `hud.vert` works in clip space directly, which is why the test triangle uses coordinates like `-1..1`. That's fine for one hardcoded triangle but miserable for laying out a hotbar. The upgrade: feed the shader **pixel coordinates** (0..width, 0..height) and let an ortho matrix convert them to clip space for you.

What to read:
- LearnOpenGL, Shaders (ins/outs, uniforms, how the vert→frag handoff works): https://learnopengl.com/Getting-started/Shaders
- JOML `Matrix4f.ortho` / `setOrtho` (your ortho builder): https://javadoc.io/doc/org.joml/joml/latest/org/joml/Matrix4f.html

The concept that trips everyone up — **which way is Y?** `ortho(left, right, bottom, top, near, far)`:
- `ortho(0, width, height, 0, -1, 1)` puts (0,0) at the **top-left**, Y increasing downward — matches how you think about screen layout ("hotbar sits at y = height - 40"). The matrix flips Y internally.
- `ortho(0, width, 0, height, -1, 1)` puts (0,0) at the **bottom-left**, Y up (math convention).

Pick one and commit; everything downstream assumes it.

Steps for 18.1:
1. Decide the vertex layout (see section 3 — this drives whether icons are possible).
2. Rewrite `hud.vert` to take a 2D position and transform it: `gl_Position = uOrtho * vec4(aPos, 0.0, 1.0)`. Add a `uOrtho` uniform. Pass color (and UV, if you're doing textures) through to the fragment stage.
3. Rewrite `hud.frag` to output the color, and — if textured — multiply by a sampled atlas color (section 7).
4. There's no `setUniformMat4` caller for the HUD yet: `Renderer` only sets `uView/uProjection/uModel`. So `HudRenderer` must set `uOrtho` **itself** — bind the program and call `setUniformMat4("uOrtho", ortho)` once at construction and again on resize. Uniforms are program state; they persist until changed, so you don't re-upload it every frame.

---

## 18.2 — HudRenderer as an IRenderable

`HudRenderer` already implements `IRenderable`. The job is to make `getDrawCalls(Camera)` return real draw calls instead of `List.of()`, and to own the meshes + ortho matrix + the references it needs (`Player` for the selected slot and hotbar contents, `TextureAtlas` for UVs, `BlockRegistry` to resolve a `Block` to its `BlockDef`).

What to read — this is the "how a vertex buffer is actually described to the GPU" page, which you'll need to extend the mesh format:
- Khronos wiki, Vertex Specification (VAO/VBO/attribute pointers, stride, offset): https://www.khronos.org/opengl/wiki/Vertex_Specification

Concepts:
- **Dirty flags.** Most HUD elements only move on a window resize (crosshair, hotbar) or a slot change (highlight, icons). Don't rebuild every frame — keep a `dirty` boolean per mesh, rebuild at the top of `getDrawCalls()` only when set. This is the same idea as chunk remeshing, just cheaper.
- **GL state for this pass.** The world draws with depth-test on, blending off. The HUD wants depth-test **off** (always on top) and blending **on** (semi-transparent slots). Because the HUD draws last, flip that state at the start of the HUD's draw calls. Then either flip it back, or rely on the next frame's setup to restore it — being explicit is less surprising. (Details in 18.4.)

Steps:
1. Give `HudRenderer` its dependencies via the constructor (wired in `GameContainer` — see the checklist).
2. Build and cache the ortho matrix; rebuild on resize.
3. In `getDrawCalls()`, rebuild any dirty meshes, set the needed GL state, and return one `DrawCall` per mesh (or per element group), each pairing a mesh with the HUD shader.

---

## 18.3 — Crosshair

A "+" made of two thin rectangles centered on screen. This is the smallest possible real HUD element and a good place to confirm your ortho pipeline works before adding textures.

Concept: in pixel space the center is `(width/2, height/2)`. One bar is e.g. 2px wide × 12px tall; the other is 12px wide × 2px tall. Each rectangle is a **quad** = 4 vertices + 6 indices (two triangles sharing an edge). If you're using the white-pixel trick (section 7) the UVs point at that white texel; if your crosshair mesh is color-only, just give it white vertices.

What to read (the "what is a quad, what are indices" basics, if the EBO still feels fuzzy):
- LearnOpenGL, Hello Triangle (VBO/EBO, why indexed drawing): https://learnopengl.com/Getting-started/Hello-Triangle

Steps:
1. Compute the four corners of each bar from the screen center.
2. Emit 8 vertices / 12 indices into one mesh, all white.
3. Mark dirty on resize so the center recomputes.

---

## 18.4 — Hotbar background, and alpha blending

Nine equal squares in a row at the bottom, each a dark semi-transparent fill.

The math: with slot size `S` and gap `G`, total width is `9*S + 8*G`. Center it: `startX = (width - total) / 2`. Put it near the bottom: `startY = height - S - margin` (assuming Y-down). All nine quads go in **one mesh** — 36 vertices, 54 indices, one draw call.

The new concept here is **blending**. "Semi-transparent" isn't free — by default OpenGL just overwrites the framebuffer. To get `result = src.rgb * src.a + dst.rgb * (1 - src.a)` you must enable blending and set the standard function (`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`). Without it, an alpha of 0.7 renders as fully opaque.

What to read:
- LearnOpenGL, Blending (the blend equation, the standard func, why order matters): https://learnopengl.com/Advanced-OpenGL/Blending
- docs.gl reference for the exact calls when you need them: https://docs.gl/gl3/glBlendFunc and https://docs.gl/gl3/glEnable

Steps:
1. Enable blending + set the blend func at the start of the HUD pass (and depth-test off).
2. Build the nine slot quads in one mesh, fill color something like black at ~0.7 alpha. (This requires alpha to reach the shader — see section 3; the current `hud.frag` hardcodes `1.0` alpha.)

---

## 18.5 — Block icons (the part that forces a mesh-format decision)

Each slot shows the top-face texture of the block it holds. Your `TextureAtlas` already has every face's UVs: `getFaceUVs(def, Direction.UP)` returns `[uMin, vMin, uMax, vMax]`. The problem is purely on the HUD side: **`TriangleMesh` carries no UVs**, so it physically cannot sample the atlas. This is the central decision of the phase (section 3).

What to read:
- LearnOpenGL, Textures (samplers, texture units, UV space, `GL_NEAREST`): https://learnopengl.com/Getting-started/Textures

The flow once your mesh has UVs:
1. For slot `i`, get the `Block` from the player's hotbar (remember it's 1-indexed: key = `i + 1`).
2. Resolve it to a `BlockDef` via `BlockRegistry`.
3. `atlas.getFaceUVs(def, Direction.UP)` → the four UV corners.
4. Map those UVs onto the slot's quad.

The classic bug — **upside-down icons.** Your atlas follows OpenGL convention: `vMin` = bottom of the image, `vMax` = top (STB flips on load; see the comment in `TextureAtlas.computeUVs`). But your screen-space Y goes *down*. So the screen-top of the icon quad should use `vMax` and the screen-bottom should use `vMin`. Get it backwards and every icon is flipped. The half-texel inset already in `computeUVs` protects you from edge bleeding for free — don't re-add it.

Bind the atlas before these draw calls (`TextureAtlas.bind()`), and the icon vertices use white color so the texture shows untinted.

---

## 18.6 — Selected slot highlight, and draw order

A bright border behind the active slot. Cheapest version: a quad ~2px larger on each side than the slot, in a bright color, drawn **behind** the icon.

The concept is the **painter's algorithm**: with depth-testing off, there's no z-buffer deciding what wins — whatever is drawn *later* paints over what came before. So order matters, back to front:
1. Hotbar background (furthest back)
2. Highlight (over background, under icon)
3. Icons (on top)

If everything's in one mesh, the **index order** sets paint order. If they're separate draw calls, the **order in the returned `List<DrawCall>`** sets it. Either way, highlight before icon.

What to read (why we can lean on paint order here instead of depth):
- LearnOpenGL, Depth Testing (what the depth buffer does, and what turning it off means): https://learnopengl.com/Advanced-OpenGL/Depth-testing

Steps: position the highlight quad from `player.getSelectedSlot()`, mark it dirty on slot change, draw it before the icons.

---

## 18.7 — Debug overlay

`DEBUG_OVERLAY` is already wired to a key but currently just logs. Real on-screen text means rendering a bitmap/atlas font — a quad per glyph with the font texture's UVs. That's a mini-version of everything above and a great stretch goal, but not required to clear the checkpoint.

What to read when you want to do real text:
- LearnOpenGL, Text Rendering (glyph atlas, one quad per character): https://learnopengl.com/In-Practice/Text-Rendering

Pragmatic version for now: keep a `debugOverlayVisible` boolean, toggle it on the action, and when it's on append diagnostics to the window title (you already update it with FPS each second) — player XYZ, targeted block, chunk queue depths. Exposing the queue depths means a small accessor/DTO on `ChunkManager`.

---

## Wiring checklist (corrected to the real APIs)

1. **`GameContainer`** — construct `HudRenderer` after the GL context exists, pass it `Player`, `TextureAtlas`, `BlockRegistry`, and the window; register it as a resize listener; add it **last** to the `Renderer` list.
2. **`Player`** — `getSelectedSlot()` already exists (0-indexed). You still need a way to read *which block is in slot i*: the map is `Map<Integer, Block>` and **1-indexed**, so add e.g. `Block getHotbarBlock(int zeroBasedSlot)` returning `slotToBlockIdHotbar.get(zeroBasedSlot + 1)` (with a sensible default). Note it returns a `Block`, not a `byte`.
3. **`Renderer`** — no change needed; it'll set the camera uniforms on the HUD shader and they'll be ignored. `HudRenderer` sets `uOrtho` itself.
4. **`Game` / input** — turn the `DEBUG_OVERLAY` stub into a real toggle and enrich the title when it's on.
5. **GL state** — manage depth-test/blending inside the HUD pass (or guarantee the world pass resets to depth-on/blend-off each frame).

General references to keep open:
- LWJGL guide (how LWJGL maps to GL, buffer management): https://www.lwjgl.org/guide
- docs.gl (per-call reference for any `glX` you hit): https://docs.gl/

---

## Common pitfalls

- **Upside-down icons** — the #1 bug. Screen-Y down vs texture-V up: screen-top uses `vMax`, screen-bottom uses `vMin`.
- **Opaque "transparent" slots** — you forgot `glEnable(GL_BLEND)` or the blend func, or alpha never reached the shader (current `hud.frag` hardcodes `1.0`).
- **HUD hidden behind blocks** — depth-test still on; the world's depth buffer rejects HUD fragments. Disable depth-test for the pass.
- **Nothing moves on resize** — you didn't rebuild the ortho matrix and/or mark meshes dirty.
- **GPU memory leak** — every mesh rebuild must `delete()` the old VAO/VBO/EBO first (`TriangleMesh.delete()` already does the three; just remember to call it).
- **Icons can't be textured at all** — that's expected until the HUD mesh format carries UVs (section 3). Solid-color elements work before that; icons don't.


STUFF

Good context now. Here's the full walkthrough:

---
The core question: where does the bucket tag come from?

BlockDef already has transparent. The mesher already visits every block and knows its BlockDef. So the identification happens in ChunkMesher.mesh() — you split the geometry into two buffers right there:

if (registry.get(blockId).transparent())  → transparent buffer
else                                       → opaque buffer

That's the only place that needs to "know." Everything downstream just carries the label.

---
The data flow

1. ChunkMeshData grows a second mesh

public record ChunkMeshData(
ChunkPos pos,
float[] opaqueVerts,   int[] opaqueIdx,
float[] transparentVerts, int[] transparentIdx,
int vertexCount, Chunk chunk) {}

2. ChunkMesher.mesh() writes to two sets of arrays

You run the same loop you have now, but branch on def.transparent() before writing verts/indices. Two vertPos counters, two idxs arrays, two vertexBase counters.

Also: isCulled needs a small fix — right now it culls if the neighbor is solid(). Water-next-to-water faces should also be culled (otherwise you get internal water surfaces). Add: || neighborBlock == blockId to the cull condition (same block = cull).

3. RenderWorld stores two meshes per chunk

Right now Entry is (model, mesh). It becomes two entries per chunk, or Entry carries (model, opaqueMesh, transparentMesh).

4. ChunkRenderer.render() does two passes

// Pass 1 — opaque
glDepthMask(true);
glDisable(GL_BLEND);
for (entry : renderWorld.getEntries()) {
shader.setUniformMat4("uModel", entry.model());
entry.opaqueMesh().render();
}

// Pass 2 — transparent (after all opaque geometry is in the depth buffer)
glDepthMask(false);   // reads depth but doesn't write — prevents water hiding water behind it
glEnable(GL_BLEND);
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
for (entry : renderWorld.getEntries()) {
shader.setUniformMat4("uModel", entry.model());
entry.transparentMesh().render();
}
glDepthMask(true);    // restore for next frame

The glDepthMask(false) is the key non-obvious part — transparent geometry needs to read the depth buffer (so it's occluded by terrain in front of it) but not write to it (so transparent surfaces behind other transparent surfaces still render).

---
Why two passes at all?

If you render a transparent face before the opaque face behind it, blending multiplies against whatever's currently in the framebuffer (background clear color), not the opaque face. The opaque pass fills the depth buffer + color, then the transparent pass blends on top with correct data.

---
What about sorting transparent faces back-to-front?

Strictly correct transparency requires sorting transparent quads by distance from camera each frame. For water that fills full chunks, you can skip this for now — the glDepthMask(false) trick handles most visible cases without sorting. When you have floating transparent objects that overlap, you'd need it.

---
Want me to start the implementation, or does any part of the flow need more clarity first?

✻ Cooked for 1m 8s

● How is Claude doing this session? (optional)
1: Bad    2: Fine   3: Good   0: Dismiss

────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
❯ start the implementation
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
Sonnet 4.6 | Minecraft master (121 files uncommitted) | ██▄░░░░░░░ 25% of 200k                                                                                                                                     ⧉ In plan.md
Session: $5.1974 | Week: 3231k tokens
⏵⏵ accept edits on (shift+tab to cycle) · ← for agents





