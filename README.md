# Minecraft Clone

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![OpenGL](https://img.shields.io/badge/OpenGL_3.3-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.opengl.org/)

## Overview
A **Minecraft clone** built from scratch in **Java 21** using **LWJGL** (OpenGL 3.3 core, GLFW) and **JOML** for 3D math. The project uses a **domain driven architecture** — the domain layer (world, blocks, player) knows nothing about OpenGL, GLFW, or threads. The project is currently in active development.

---

## Controls
| Key | Action |
|-----|--------|
| `WASD` | Move |
| `Mouse` | Look |
| `Left Click` | Break block |
| `Right Click` | Place block |
| `Scroll Wheel` | Cycle hotbar |
| `1`–`9` | Select hotbar slot |
| `Space` | Jump |
| `Double-tap Space` | Toggle creative fly |
| `Left Shift` | Sneak |
| `X` / `Esc` | Quit |

---

## Architecture

```
src/main/java/com/beneklund/minecraft/
  block/           Pure domain — block types, BlockDef, BlockRegistry
  world/           Pure domain — Chunk, World, IWorldAuthority, ChunkState machine
    gen/           World generation (pure factory: ChunkPos + seed → Chunk)
  player/          Pure domain — Player, IPhysicsBody, Physics, PlayerState
  util/            Stateless utilities — Raycast (DDA), AABB, Direction, OpenSimplex2
  renderer/        Rendering logic — ChunkMesher, Camera, ShaderProgram, TextureAtlas
  platform/
    window/        GLFW window lifecycle and game loop
    input/         Raw GLFW events → InputAction (anti-corruption layer)
    graphics/      OpenGL objects — GlShader, GlBuffer, GlVertexArray, GlTexture, GpuMesh
  infra/           Infrastructure — ChunkManager (thread pools, queues),
                   SaveFile + ChunkStore + PlayerStore (persistence)
  container/       GameContainer — DI composition root, all `new` calls live here;
                   ContainerConfig carries every launch knob
```

---

## Saves

Worlds live in `saves/<seed>/` — chunks as `<x>_<z>.bin`, the player as `level.dat`. Both
share one binary layout: a 12-byte header (magic number, format version, payload length)
followed by the payload.

Reads validate the magic and length and fall back to "no save" instead of throwing, so a
foreign or truncated file regenerates rather than crashing the game. Writes go to a temp
file and are moved into place atomically, so a crash mid-save can't leave a half-written
world behind.

The save directory is derived from the world seed, so changing the seed starts a fresh
world rather than overwriting an existing one. `saves/` is gitignored.

---

## Getting Started

### Prerequisites
- [**Java 21**](https://www.azul.com/downloads/) (Azul Zulu recommended)
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
