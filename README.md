# Minecraft Clone

[![Java](https://img.shields.io/badge/Java_25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![OpenGL](https://img.shields.io/badge/OpenGL_3.3-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.opengl.org/)

## Overview
A **Minecraft clone** built from scratch in **Java 25** using **LWJGL** (OpenGL 3.3 core, GLFW) and **JOML** for 3D math. The project uses a **domain driven architecture** — the domain layer (world, blocks, player) knows nothing about OpenGL, GLFW, or threads. The project is currently in active development.

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
- [**Java 25**](https://www.azul.com/downloads/) (Azul Zulu recommended)
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
