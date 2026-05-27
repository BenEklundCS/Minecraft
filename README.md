# Minecraft Clone

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![OpenGL](https://img.shields.io/badge/OpenGL_3.3-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.opengl.org/)

## Overview
A **Minecraft clone** built from scratch in **Java 21** using **LWJGL** (OpenGL 3.3 core, GLFW) and **JOML** for 3D math. The project follows **Hexagonal Architecture** — the domain layer (world, blocks, player) knows nothing about OpenGL, GLFW, or threads. The project is currently in active development.

---

## Key Features

- **Procedural World Generation:**
  OpenSimplex2 noise with multiple octaves drives terrain height, biome selection (plains, forest, mountains, desert, ocean), ore placement, cave carving, and tree spawning — all deterministic from a seed.

- **Chunk Streaming:**
  The world loads and unloads 16×16 column chunks around the player. A background thread pool handles generation and mesh building; the main thread drains an upload queue each frame to keep GL calls single-threaded.

- **OpenGL 3.3 Renderer:**
  Greedy face culling, a texture atlas with per-face UV lookup, per-vertex ambient occlusion, face-direction lighting, distance fog, and frustum culling. Transparent blocks (glass, leaves, water) are sorted and drawn in a second pass.

- **Hexagonal Architecture:**
  The domain (`block/`, `world/`, `player/`) is pure Java — no GL, no GLFW, no thread primitives. Platform adapters (`platform/`) are the only code that calls LWJGL directly. `GameContainer` is the sole composition root; all dependencies flow in through constructors.

- **Input Anti-Corruption Layer:**
  Raw GLFW keycodes never leave `platform/input/`. An `InputMapper` translates them into a sealed `InputAction` hierarchy (`MoveAction`, `LookAction`, `Simple`) consumed by the game loop. Rebinding a key means changing the mapper only.

- **AABB Physics & Collision:**
  Axis-separated sweep collision against solid blocks. `Physics` acts on the `IPhysicsBody` interface so future entities get the same system for free.

- **Block Interaction:**
  Left-click breaks the targeted block; right-click places the selected hotbar block on the hit face. A DDA raycast (Amanatides & Woo) finds the target. Block changes dirty the chunk (and its neighbors at seams) for async re-mesh.

- **Chunk State Machine:**
  Each `Chunk` owns an `AtomicReference<ChunkState>` with CAS transitions:
  `UNLOADED → QUEUED_GEN → GENERATING → QUEUED_MESH → MESHING → READY_TO_UPLOAD → UPLOADED → DIRTY → …`

---

## Controls
| Key | Action |
|-----|--------|
| `WASD` | Move |
| `Mouse` | Look |
| `Left Click` | Break block |
| `Right Click` | Place block |
| `Scroll Wheel` | Cycle hotbar |
| `Space` | Jump |
| `X` | Quit |

---

## Architecture

```
src/main/java/com/beneklund/minecraft/
  block/           Pure domain — block types, BlockDef, BlockRegistry
  world/           Pure domain — Chunk, World, IWorldAuthority, ChunkState machine
    gen/           World generation (pure factory: ChunkPos + seed → Chunk)
  player/          Pure domain — Player, IPhysicsBody, Physics
  util/            Stateless utilities — Raycast (DDA), AABB, Direction, OpenSimplex2
  renderer/        Rendering logic — ChunkMesher, Camera, ShaderProgram, TextureAtlas
  platform/
    window/        GLFW window lifecycle and game loop
    input/         Raw GLFW events → InputAction (anti-corruption layer)
    graphics/      OpenGL objects — GlShader, GlBuffer, GlVertexArray, GlTexture, GpuMesh
  infra/           Infrastructure — ChunkManager (thread pools, queues), ChunkStore (save/load)
  container/       GameContainer — DI composition root, all `new` calls live here
```

---

## Getting Started

### Prerequisites
- [**Java 21**](https://adoptium.net/) (Temurin recommended)
- Gradle wrapper included — no separate install needed

### Recommended IDE
[**IntelliJ IDEA**](https://www.jetbrains.com/idea/) with the Gradle plugin provides the best experience for this project.

### Run

```bash
git clone https://github.com/BenEklundCS/Minecraft.git
cd Minecraft
./gradlew run
```

### Test

```bash
./gradlew test
```

Test reports are written to `build/reports/tests/test/index.html`.
