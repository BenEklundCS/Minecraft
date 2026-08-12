# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Java 21 with `--enable-preview` (Zulu 21 locally). On Windows use `.\gradlew.bat`; the Bash
tool can use `./gradlew`. If Gradle picks a wrong JVM, set `JAVA_HOME` to the Zulu 21 install
for that invocation.

```bash
./gradlew run                      # launch the game
./gradlew build                    # compile + spotlessCheck + checkstyle + unit tests
./gradlew test                     # unit tests only (integration excluded)
./gradlew test --tests "com.beneklund.minecraft.player.PhysicsTest"   # single test class
./gradlew test --tests "*PhysicsTest.gravityAccelerates"              # single test method
./gradlew integrationTest          # slow suite under src/test/**/integration/ — NOT part of build
./gradlew spotlessApply            # format (palantir-java-format); pre-commit hook runs this
./gradlew checkstyleMain checkstyleTest
```

Test reports: `build/reports/tests/test/index.html`.

`build` also runs `installGitHooks`, which points `core.hooksPath` at `.githooks/` — the
pre-commit hook runs `spotlessApply` and re-stages, so CI's `spotlessCheck` never trips.

CI (`.github/workflows/test-and-build.yml`) runs `./gradlew build` plus a 5-second Xvfb smoke
run of the game; a startup crash fails the build.

### Runtime knobs

- **Logging:** `-Dlog.all=DEBUG`, or per category `-Dlog.chunk=TRACE`. Categories are
  `chunk, world, render, gpu, input, player, io, audio, perf` (see `util/Log.java`,
  `resources/logback.xml`). Logback re-scans every 5s, so levels can change mid-run.
- **`local.properties`** (repo root, gitignored, optional): `startup.disc`, `preferred.album`,
  `debug.enabled`. Read by `container/LocalConfig`.
- **Shader hot reload:** the `RELOAD_SHADERS` input action calls `renderer.reloadAll()` in-game.
- **Launch config:** `container/ContainerConfig.defaults()` — seed, render distance, FOV,
  window, resource pack, spawn.

## Architecture

Hexagonal layering without the ceremony.

`docs/` is gitignored — it exists in a local checkout but not on the remote, so don't assume
it's there. When it is, it's the best starting point: `ARCHITECTURE.md` for the design,
`decisions/` for the ADRs behind it, `STATE_OF_PLAY.md` for what currently works or is broken,
`BACKLOG.md` and `roadmaps/` for what's next.

The essentials that cross many files:

**Dependency rule.** `world/`, `player/`, `block/`, `entity/` depend on nothing outside
themselves — no GL, no GLFW, no threads. `renderer/` may use `platform/graphics/` and the
domain. `infra/` may use everything. `container/GameContainer` is the only place that calls
`new` on platform/renderer types.

**Composition root ordering is load-bearing.** `GameContainer.run()` has numbered phases;
nothing above `window.init()` may touch GL. Shutdown runs in reverse and stops chunk workers
*before* flushing dirty chunks, so no async `setBlock` can dirty an already-persisted chunk.

**Thread model.** The main thread is the *only* thread allowed to call OpenGL. Two fixed pools
(`availableProcessors/2`, min 2) do generation and meshing; they produce plain data
(`ChunkMeshData`: `float[]`/`int[]`) that the main thread uploads, capped at
`MAX_UPLOADS_PER_FRAME = 4` in `Game.processChunks()`. `RenderWorld` is render-thread-only.
Adding a GL call to a worker is the failure mode this design exists to prevent.

**Chunk pipeline** (`infra/ChunkManager`): each tick computes the load radius in spiral order,
enqueues generation (or loads from `ChunkStore` and skips straight to meshing), remeshes
`DIRTY` chunks, and evicts chunks outside the radius (saving them first). `Chunk` owns its
state machine via `tryTransition` on an `AtomicReference<ChunkState>` — every job re-checks the
transition and bails if it lost the race. `ChunkManager` inserts an empty `Chunk` into `World`
*before* the worker fills it, so "present in the map" ≠ "has blocks": `meshable()` and
`Game.physicsReady()` both gate on state for exactly this reason.

**`IWorldAuthority`** is the seam all domain reads/writes go through (`LocalWorldAuthority`
today, a remote impl later). `IPhysicsBody` is the same idea for physics: `Physics` is a
system acting on the interface, not a method on `Player`.

**Rendering is passive.** Subsystems implement `IRenderable` and hand back `List<DrawCall>`;
`Renderer` sorts opaque → transparent and issues them. GL handles live in `platform/graphics/`
(`GlShader`, `GlTexture`, `GlVertexArray`); `renderer/` holds the domain-facing wrappers
(`ShaderProgram`, `TextureAtlas`).

**Input never leaks keycodes.** GLFW callback → `platform/input/InputEventQueue` →
`InputMapper` → `List<IInputAction>` (sealed hierarchy in `input/`). Rebinding touches
`InputMapper` and nothing else.

**Content is data.** `BlockRegistry` is constructor-injected, never a singleton. Textures come
from a JSON resource pack (`resources/packs/faithful/pack.json`).

**Saves** live in `saves/<seed>/` — `<x>_<z>.bin` per chunk, `level.dat` for the player, both
with a 12-byte magic/version/length header. Reads validate and fall back to "no save" rather
than throwing; writes go temp-file-then-atomic-move.

## Conventions

- **Checkstyle enforces `this.` on overlapping field accesses.** Spotless has a custom
  `removeRedundantThis` step that strips the non-overlapping ones before palantir formats.
  Run `spotlessApply` rather than hand-formatting.
- Fence hand-aligned data (vertex arrays and the like) with `// spotless:off` / `// spotless:on`.
- Comments read like a developer leaving notes for the next person: why this shape, what
  breaks otherwise, what was tried. Not doc templates, not ALL-CAPS section banners. Several
  non-obvious decisions are recorded as long comments in place (`Game.processPhysics`,
  `GameContainer.defaultSpawn`, `ChunkManager.shutdown`) — extend that habit.
- Prefer the category loggers (`CHUNK.debug`, `RENDER.trace`) over the bare `LOGGER`; `LOGGER`
  is for startup/shutdown and anything that isn't one subsystem.
- **Docs describe reality.** If a doc and the code disagree, the doc is the bug — fix it in the
  same commit. New architectural decision → new numbered ADR in `docs/decisions/`. Finishing a
  backlog item means updating `docs/BACKLOG.md` *and* `docs/STATE_OF_PLAY.md`.
- Because `docs/` never reaches the remote, anything a stranger cloning the repo needs to know
  belongs in `README.md` or in a comment next to the code that enforces it — not only in `docs/`.
- `private/` is a separate nested git repo (personal notes) and is not part of this repo's
  history.
