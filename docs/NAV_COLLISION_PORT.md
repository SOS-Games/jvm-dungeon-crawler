# Navmesh, pathfinding and collision: jvm-mw → jvm-dungeon-crawler

Research only. No changes, commits or PRs were made to either repo.
Sources: `SOS-Games/jvm-mw` @ `9485d74` (latest commit 2026-09-22) and `SOS-Games/jvm-dungeon-crawler` @ `6093f3a`, plus Maven Central metadata checked on 2026-10-09.

---

## TL;DR

* **jvm-mw uses two libraries for all of this:** **recast4j 1.5.7** (Recast + Detour, zlib) for the navmesh and path queries, and **gdx-bullet 1.14.0** (JNI Bullet 2.x, Apache-2.0 wrapper + zlib Bullet) for collision. It does **not** use gdx-ai, jbullet, ode4j, jolt-jni, detour-crowd or detour-tile-cache. The only other nav-related dependencies are `sqlite-jdbc` and `lz4-java`, and they exist only to read OpenMW's `navmesh.db`.
* **Character controller:** jvm-mw has no rigid bodies. It uses a hand-written **kinematic capsule mover** (`BulletWorld.move`, a port of OpenMW's `MovementSolver`/`Stepper`) built on `btCollisionWorld.convexSweepTest`, `contactTest` and `rayTest`. This is the most reusable piece.
* **The dungeon crawler already declares the same dependencies** (`gdx-bullet` and `recast4j recast/detour 1.5.7` in `core`, and `gdx-bullet-platform` natives in `lwjgl3`), but **no code uses them yet**. It is still a model viewer: no world geometry, movement, collision or pathing.
* **Recommendation:** stay on the same stack (Bullet collision world + ported kinematic capsule + recast4j tiled navmesh + Detour queries), and add `detour-crowd` once several monsters move. The crawler can feed Recast its **Y-up GL coordinates directly** with no axis swap or scale, which avoids most of jvm-mw's TES/Recast coordinate juggling and the two bugs listed in §1.7. The core of this is about **7–9 dev-days**, and crowd and dynamic doors add **2–4 more**.

---

## 1. jvm-mw inventory

### 1.1 Dependencies (`core/build.gradle`, `lwjgl3/build.gradle`, `gradle.properties`)

| Artifact | Version | Module | License | Used for |
|---|---|---|---|---|
| `com.badlogicgames.gdx:gdx` | 1.14.0 (`gdxVersion`) | core `api` | Apache-2.0 | engine |
| `com.badlogicgames.gdx:gdx-bullet` | 1.14.0 | core `api` | Apache-2.0 wrapper; Bullet itself zlib | collision world, sweeps, rays |
| `com.badlogicgames.gdx:gdx-bullet-platform:…:natives-desktop` | 1.14.0 | lwjgl3 | same | JNI natives |
| `org.recast4j:recast` | 1.5.7 | core `implementation` | zlib | navmesh bake |
| `org.recast4j:detour` | 1.5.7 | core `implementation` | zlib | navmesh + `NavMeshQuery` |
| `org.xerial:sqlite-jdbc` | 3.47.1.0 | core | Apache-2.0 | read OpenMW `navmesh.db` |
| `org.lz4:lz4-java` | 1.8.0 | core | Apache-2.0 | decompress db tile blobs |

Not used: gdx-ai, jbullet, ode4j, jolt-jni, recast4j `detour-crowd` / `detour-tile-cache` / `detour-dynamic` / `detour-extras`.

Newer versions on Maven Central: recast4j **1.5.12** (June 2026, compiled for Java 17 where 1.5.7 was Java 8; the `Result<>` API is still present), gdx **1.14.2**, gdx-ai **1.8.2** (2019).

Design rules from `AGENTS.md`, `docs/physics.md` and `docs/nav-paths.md`: Bullet is the only physics engine ("do not vendor a second"). Bullet runs on the GL thread only. Recast and sqlite run on worker threads. The headless `debugCli` must not load Bullet natives.

### 1.2 Key files (all in `core/src/main/java/io/github/jvmmw/`)

| File | Lines | Role |
|---|---|---|
| `render/BulletWorld.java` | 1030 | Static singleton `btCollisionWorld` (default config, dispatcher, `btDbvtBroadphase`, `setForceUpdateAllAabbs(false)`). Land is one `btBvhTriangleMeshShape` per TES cell (65×65 grid via a single `btIndexedMesh` upload). Objects are `btTriangleMesh` per placement. Collision groups WORLD=1, ACTOR=4, HEIGHT_MAP=8, PROJECTILE=16. Contains the kinematic capsule mover, spawn snap, ray helpers, and live door/item followers. |
| `render/CollisionMesh.java` | 230 | Per-NIF interned collision triangles. Rules: `RootCollisionNode` if it has children, else render tris; `NC` means none; `MRK` handling. |
| `render/CollisionTris.java` | 70 | Thread-safe world-space snapshot of object tris for the Recast worker (`appendObjectTris(minX,maxX,minZ,maxZ)`). |
| `render/NavmeshBaker.java` | 454 | Recast config and bake: `bakeRuntime` (whole cell, GL space) and `bakeRecastTile` (one tile, "TES/sqlite space"). Also land tris, seam-stitch quads, bilinear `tesHeight`. |
| `render/NavmeshQuery.java` | 257 | One Detour `NavMesh` + `NavMeshQuery` per worldspace. `findNearestPoly` → `findPath` → `findStraightPath`. |
| `render/NavmeshCache.java` | 454 | Two daemon single-thread executors (`navmesh` for sqlite/bake, `navmesh-patch` for rebaking cracked tiles), a process-wide tile cache, and a ready queue polled by the GL thread for overlay upload. |
| `render/NavmeshDb.java` | 471 | Reads OpenMW `navmesh.db` (`pnav` blobs, lz4) into recast4j `PolyMesh`/`PolyMeshDetail`. |
| `render/NavmeshGaps.java` | 326 | Finds Recast tiles whose db tris leave a walkable TES cell edge uncovered (input to the patch pass). |
| `render/NavmeshDebug.java` | 184 | F6 green carpet overlay; tiles waiting for a rebake show as red squares. |
| `esm/EsmPathgrid.java`, `esm/PathgridGraph.java` (349) | | TES3 `PGRD` parse, connected components, A* (`PriorityQueue`) over authored nodes. OpenMW-derived; keeps the GPL port header. |
| `render/PathgridDebug.java` | 218 | F5 pathgrid overlay. |
| `render/NpcMannequin.java` | 2106 | Actor AI: wander/travel/follow/escort. `followDetour`, `stepPath` (polyline follow, arrive radius), `stickLand` (Bullet down-ray for Z), `occupied` (destination check only). |
| `debug/NavmeshDbDump.java`, `debug/DebugCli.java` | | Headless `navdb` / `navpath` / `pgrd` dumps. |
| Docs | | `docs/nav-paths.md`, `docs/physics.md`, `docs/phase40`–`phase51`. Every slice is specced with pass/fail. |

### 1.3 How the navmesh is built

* **Source priority:** (1) OpenMW's prebuilt `navmesh.db` (from `openmw-navmeshtool`) for the loaded 5×5 cells, or for all tiles of an interior; (2) when that is missing, a runtime Recast bake from **land heightfield triangles + `CollisionTris` object triangles** (NIF collision geometry at its placed world matrices); (3) after sqlite has loaded, a **patch pass** finds cracked tiles (`NavmeshGaps`) and rebakes only those on the second thread, then swaps them in with `NavMesh.updateTile`.
* **Units:** Recast space is TES units × `SCALE = 1/34` (≈0.0294, OpenMW's `toNavMeshCoordinates`). "TES space" tiles are `(x, height, y) × scale`. Runtime-bake tiles are GL `(x, y, z) × scale` with z = −tesY, flagged `tesSpace=false`.
* **Recast parameters** (`NavmeshBaker.recastConfig`, matching OpenMW `[Navigator]` defaults):
  * cs = ch = 0.2 nav units (≈6.8 TES); tile 128×128 cells (25.6 nav ≈ 870 TES); border 16 cells; WATERSHED partition.
  * Max slope 46°. Filters on: low-hanging obstacles, ledge spans, low-height spans.
  * Agent: OpenMW default AABB half-extents 29.28 × 28.48 × 66.5 TES.
    * walkableHeight = 2·66.5·scale ≈ **3.91** (133 TES).
    * walkableRadius = max(halfX, halfY)·√2·scale ≈ **1.22** (41.4 TES).
    * walkableClimb = 34·scale = **1.0** (34 TES, same as the physics step-up).
  * minRegionArea 64 cells, mergeRegionArea 400 cells, edgeMaxLen 12, edgeMaxError 1.3, vertsPerPoly 6, detail sampleDist 6, maxError 1, area 63.
* **Detour mesh** (`NavmeshQuery.newWorld`): orig = (0,0,0), tileWidth/Height = 25.6, maxTiles 8192, maxPolys 4096 per tile, nvp 6, poly flag WALK=1 for any poly with a non-zero area. Tiles are added with `addTile` or replaced with `updateTile`.
* **Seam stitch:** `addSeamStitch` adds 256-TES-wide quads along TES cell lines so Recast erosion doesn't open thin cracks between cells.

### 1.4 Queries, smoothing, steering

* `NavmeshQuery.find`: `findNearestPoly` with half-extents equal to 4× the agent AABB, then `findPath` (polyref corridor, capped at 1024), then `findStraightPath` (string-pulled corners). This is the only "smoothing". It does not use `raycast` shortcuts, `moveAlongSurface`, or a corridor/`PathCorridor` re-plan. It returns an empty list while any navmesh job is running or when there is no path, and the caller then walks a straight line.
* **Who uses it:** wander falls back to it when the cell pathgrid is unusable, and travel/follow/escort use it when the pathgrid A* misses (`NpcMannequin.armWalkTo` → `followDetour`). Authored pathgrid A* is always tried first.
* **Following:** `stepPath` walks waypoints with a fixed arrive radius (`WANDER_ARRIVE`), a yaw turn-rate limit, and the walk clip's speed. Z comes from a Bullet down-ray (`stickLand`). There is **no crowd, local avoidance or separation**. The only multi-agent logic is `occupied()`, which rejects a destination another actor stands on.

### 1.5 Collision setup

* **World:** `btCollisionWorld` (no dynamics world), dbvt broadphase, all bodies `CF_STATIC_OBJECT`, filtered with OpenMW-style groups. Rebuilt on interior load and walk-grid swap.
* **Shapes:** land is a BVH triangle mesh (not `btHeightfieldTerrainShape`), one per cell, indexed upload with short indices. Objects are a `btTriangleMesh` per placement, **with every triangle added in both windings**. Doors and takeable items follow their `SceneNode` (`syncFollowers`, `updateSingleAabb`). Everything else is baked at load pose.
* **Player controller (`move`)** uses the OpenMW constants: eye 96, height 128, radius 30, step up 34, step down 62, ground offset 1, max slope 46°, gravity 627, margin 0.2 (TES units). The capsule is `btCapsuleShape(30, 128-60)` and is never added to the world. Each frame:
  1. Split the horizontal move into ≤16-unit parts.
  2. Per part, `slide`: a heightmap cliff pre-check, then a sweep; on a hit, step up / sweep forward / sweep down, else take the hit fraction and slide along the wall's horizontal normal (with a 2-unit push-off retry).
  3. Vertical sweep for fly/dolly input.
  4. Ground snap within the step-down distance, else gravity fall with a sweep.
  5. Ceiling sweep.
  6. `depenetrate` with up to 8 `contactTest` push-outs.
* **NPCs** are not bodies. Their feet use `hitY` (World|HeightMap ray). They clip shacks and each other (`physics.md` rows 4–5 are still open).

### 1.6 Open WIP items, as the repo's own docs describe them

* Actor vs world capsules, actor vs player, actor vs actor (`physics.md` §4–5).
* Detour-first wander, water/swim surface, off-mesh links, per-actor agent sizes, own tile cache / navmesh writer (`nav-paths.md` §6).
* Occupied/hidden destination checks via line of sight (`nav-paths.md` §4); a shared `PathFinder` (§5).
* The navmesh does not update for swung doors. Chest lids have no hull. Projectiles and swim are not implemented.

### 1.7 Bugs and risks I noticed (not filed; verify before relying on them)

1. **Bake-fallback tiles land at the wrong Detour grid cells (high confidence).** `NavmeshBaker.bakeRuntime` calls `RecastBuilder.buildTiles(geom, …)`. In recast4j, that numbers tiles **relative to the geometry's `bmin`** (`RecastBuilderConfig`: `bmin[0] += tileX * tsx`). `NavmeshQuery` builds the `NavMesh` with `orig = 0` and adds tiles by `tile.x/tile.y`, while Detour finds tiles by world position from `orig`. Unless `bmin` happens to sit on a 25.6 multiple, `findNearestPoly` looks up the wrong tiles. Two bakes of different cells can also both claim tile (0,0), and the second `addTile` throws and is silently dropped. This only affects the no-`navmesh.db` path: interiors without db tiles, or users without umo. The `bakeRecastTile` patch path is correct because it passes `bmin = 0` with explicit tx/ty. **Fix:** snap bounds to the tile grid and build each tile with an explicit `RecastBuilderConfig`, as `bakeRecastTile` already does.
2. **Possible winding flip for object triangles in TES-space patches (medium confidence).** `gatherTesRecast` converts object triangles GL→Recast with `(x, y, -z)`. That is a reflection, so the triangle winding reverses, and Recast's `markWalkableTriangles` decides walkability from winding (`norm[1] > cos(slope)`). Land in the same function is built directly in TES order with the correct winding. So dock tops or floors inside a *patched* tile may come out unwalkable. **Check:** a patched tile that covers a dock should still show a carpet on the boards. If it doesn't, reverse the index order of mirrored object triangles.
3. **Mixed tile spaces per world.** `World.tesSpace` is taken from the **first** tile added. GL-space bake tiles (`tesSpace=false`) and db/patch tiles (`true`) in the same world would mirror queries. This is avoided today only because the patch pass is skipped when `source == "bake"`.
4. `wanderPath` returns nothing whenever **any** navmesh job is busy (a global counter, not per world), so Detour is off during every ring load.
5. Object meshes add each triangle twice (both windings). Bullet triangle meshes already collide two-sided for convex sweeps and rays, so this doubles BVH size and contact work with no benefit. There is also no internal-edge fix (`btGenerateInternalEdgeInfo`), which can cause snags on triangle seams; `depenetrate` hides most of it.
6. Everything is a static global (`BulletWorld`, `NavmeshQuery`, `NavmeshCache`): one world per process. Bullet is GL-thread only, and Detour queries hold a world lock. This is fine for the viewer but has to become instance classes before it can be shared.

---

## 2. jvm-dungeon-crawler today

* **Build:** libGDX 1.14.0, Java 21, `core` + `lwjgl3`. `core` has the **same** `api gdx-bullet`, `implementation recast4j recast/detour 1.5.7` as jvm-mw, plus ANTLR 4.13.2 for the RON converter. `lwjgl3` already ships `gdx-bullet-platform:natives-desktop`. **Nothing imports Bullet or recast4j** (grep finds no hits), so these are template leftovers. `Bullet.init()` is never called.
* **Code (13 files, ~7.3k lines):** `JvmDcApp` (browser grid + orbit viewer, drag to rotate), `ForwardRenderer` (one shader), `converter/*` (RON→JSON, `.vox` reader, `VoxMesher`), and `viewer/*` (`NpcFigure`, `AnimalPose`, `FigurePose`, `LargePose`, `VoxCatalog`).
* **World geometry:** none. There are no dungeon, terrain, chunks or scene graph. `VoxMesher` makes render meshes for single models, with per-face culling (**not greedy**). Each model is centred on its own origin, 1 voxel = 1 GL unit, and has a position + normal + packed-colour vertex format with 16-bit index splits.
* **Coordinates:** `VoxMesher.corner` maps voxel Z-up to GL Y-up as `(x, y, z) → (x, z, −y)`. That is a **proper rotation** (determinant +1), so winding is preserved. jvm-mw uses the same mapping for TES→GL. The skeleton math (`AnimalPose`, `NpcFigure`) stays Z-up, and the renderer is Y-up.
* **Movement, collision, pathing:** none. The camera only orbits the selected model.

So the crawler needs everything from the world representation upward. The dependencies are already declared.

---

## 3. Recommendation for the dungeon crawler

### 3.1 Library choice

| Need | Recommended | Why | Alternatives considered |
|---|---|---|---|
| Collision queries | **gdx-bullet 1.14.x** `btCollisionWorld` (no dynamics yet) | Already declared and proven in jvm-mw; natives already in lwjgl3; same API, so code ports straight over | jolt-jni 6.1.1 (MIT, excellent `CharacterVirtual`, but a second JNI stack and new API); ode4j 0.5.4 (pure Java, LGPL/BSD, weak character support); jbullet (dead); pure-Java voxel AABB sweep à la Veloren `phys/collision.rs` (no natives, exact for block worlds, but no reuse with jvm-mw) |
| Character controller | **Port jvm-mw's kinematic capsule** (`BulletWorld.move` / `slide` / `depenetrate` / `snapSpawn`) into an instance class | Known feel, handles step/slope/ceiling and walls, no rigid-body jitter | Bullet's `btKinematicCharacterController` + `btPairCachingGhostObject` (known jittery in 2.8x, bad slope handling) |
| Navmesh bake | **recast4j `recast`**, tiled, on a worker thread | Same as jvm-mw; voxel boxes rasterise exactly when cs divides the block size | Building the Recast `Heightfield` straight from voxel columns (fastest, but uses recast4j internals; worth it later for very large levels) |
| Path queries | **recast4j `detour`** `NavMeshQuery` | Same as jvm-mw | gdx-ai `IndexedAStarPathFinder` over a block grid (fine for strictly tile-based rooms, but needs your own string-pulling and gives no agent-radius erosion) |
| Multi-monster steering | **recast4j `detour-crowd`** (`Crowd`, `CrowdAgentParams`) | Path corridor, local avoidance and separation, none of which jvm-mw has | gdx-ai steering behaviours |
| Doors / destructible blocks | `detour-tile-cache` (obstacles) **or** just rebake the affected 1–2 tiles on the worker (jvm-mw's `updateTile` pattern) | Rebaking is simpler and already proven in jvm-mw | `detour-dynamic` (voxel-based dynamic navmesh, which suits voxels well but is less documented) |
| AI decisions (optional) | gdx-ai 1.8.2 behaviour trees / FSM | Apache-2.0, pure Java | Hand-written state machines, as in jvm-mw |

### 3.2 Exact Gradle changes (all in `core/build.gradle` unless noted)

```gradle
// core/build.gradle — already present, keep:
api "com.badlogicgames.gdx:gdx-bullet:$gdxVersion"

// bump recast4j (Java 17 bytecode, fine on 21) and keep every recast4j artifact on ONE version:
def recast4jVersion = '1.5.12'          // or stay on 1.5.7 to match jvm-mw exactly
implementation "org.recast4j:recast:$recast4jVersion"
implementation "org.recast4j:detour:$recast4jVersion"
implementation "org.recast4j:detour-crowd:$recast4jVersion"        // phase 6
// implementation "org.recast4j:detour-tile-cache:$recast4jVersion" // only if you choose obstacles over tile rebakes

// optional:
// implementation "com.badlogicgames.gdx:gdx-ai:1.8.2"
```

```gradle
// lwjgl3/build.gradle — already present, keep:
implementation "com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop"
```

Notes:
* Do **not** add `sqlite-jdbc` or `lz4-java`. They exist only for OpenMW's `navmesh.db`.
* Moving to 1.5.12 should be a recompile-and-smoke-test. If you plan to share code with jvm-mw, bump both repos together.
* `gdxVersion` 1.14.0 → 1.14.2 is optional. Keep `gdx-bullet` on the same `$gdxVersion`.
* Call `Bullet.init()` once in `JvmDcApp.create()`. Keep it out of the headless `converter` task.

### 3.3 Feeding dungeon/voxel geometry into collision and navmesh

1. **Pick world units first.** Veloren terrain uses 1 block = 1 m, while figure `.vox` voxels are drawn much smaller (body-specific scale). Today the crawler draws figures at 1 voxel = 1 unit. Recommendation: **1 GL unit = 1 m (= 1 Veloren block)**, and scale figure meshes by their body scale when placing them. All physics and nav constants below are in metres. Bullet behaves best with objects from about 0.05 to 10 units, so this matters.
2. **Dungeon storage:** a block volume in chunks (for example 32×32×32), in voxel Z-up like Veloren. Convert to GL only when emitting vertices, using the existing `(x, z, −y)` rotation.
3. **A collision mesher separate from `VoxMesher`.** Positions only, **greedy-merged** faces per chunk, and only faces between solid and air. This reduces triangle count roughly 10–50× for walls and floors. Use int indices, or keep each chunk under 65k vertices if you copy jvm-mw's `btIndexedMesh` short-index upload.
4. **Bullet:** one `btBvhTriangleMeshShape` per chunk via `btTriangleIndexVertexArray`/`btIndexedMesh` (jvm-mw `cookLandMeshBody` pattern: one upload, not a JNI call per triangle). Add each triangle **once**. Optionally run `btGenerateInternalEdgeInfo` to avoid snagging on greedy-quad seams. When a block changes, rebuild only that chunk's body.
5. **Recast:** feed the **same chunk triangles in GL Y-up coordinates unchanged.** Recast is Y-up too, so there is no axis swap or scale and the winding is preserved, which avoids jvm-mw bug #2. Suggested config for a 1 m block world with a humanoid about 1.8 m tall and 0.4 m in radius:
   * cs = 0.25, ch = 0.125 (both divide 1 m evenly, so block edges rasterise exactly).
   * Tile 64 cells = 16 m (or 128 = 32 m); border = ceil(radius/cs) + 3.
   * walkableHeight 1.8, walkableRadius 0.4.
   * **walkableClimb 1.05** if 1-block steps must be walkable (Veloren auto-climbs about one block), otherwise 0.55 for half-slabs.
   * Max slope 46° matters little with voxels but keeps stairs-as-ramps working.
   * Watershed partition (monotone for very long thin corridors).
   * Keep the physics `STEP_UP` equal to `walkableClimb`, as jvm-mw does with 34/34.
   * A larger monster class needs a second navmesh with its own radius and height (jvm-mw has only one agent).
6. **Tiles and origin:** fix `NavMeshParams.orig` (for example (0,0,0) or the dungeon min snapped to the tile size) and build each tile with `new RecastBuilderConfig(cfg, origBmin, origBmax, tx, ty)`, so the tile index maps to world position. Do **not** use `buildTiles(geom)` on per-chunk geometry with a different origin (jvm-mw bug #1). Gather triangles for tile bounds plus border from neighbouring chunks.
7. **Threading:** bake on a single daemon executor, then hand finished `MeshData` to the GL/game thread through a `ConcurrentLinkedQueue` (the `NavmeshCache` pattern). Keep one `NavMeshQuery` per thread, or synchronise as jvm-mw does.

### 3.4 What to copy or share from jvm-mw

Most jvm-mw classes are tied to TES types (`LandRecord`, `EsmFile.LoadedCell`, `SceneNode`, `CollisionMesh.Pending`, the `SCALE` constant), so **copy and generalise first, and extract a shared module later**.

| jvm-mw source | Take? | What to change |
|---|---|---|
| `BulletWorld.move/slide/sweepPose/sweepY/depenetrate/snapSpawn/refreshDebug`, plus `DeepestContact` and `Hit` | **Yes (highest value)** | Turn it into an instance `KinematicCapsule` with a config record (radius, height, eye, stepUp, stepDown, maxSlope, gravity, margin, groundOffset) and a `btCollisionWorld` reference. Remove the TES heightmap cliff pre-check in `slide` (or replace it with a voxel column test) and `PerfTrace`. Rescale constants to metres (for example margin 0.02–0.04). |
| `BulletWorld` world lifecycle, `Staged`/`adopt`, `cookLandMeshBody` indexed upload, filter groups | Yes (pattern) | Becomes `PhysicsWorld` with per-chunk bodies; use the same group/mask idea (WORLD, ACTOR, PROJECTILE). |
| `NavmeshBaker.recastConfig`, `bakeRecastTile(float[] verts, tx, ty)` | Yes | Use metre params; gather from chunk triangles; pass the real `bmin/bmax` origin. |
| `NavmeshQuery.meshData`, `walkFlags`, `newWorld`, `find` | Yes, almost verbatim | Remove `tesSpace` and `SCALE` (identity coordinates); pass agent params; return `Vector3` lists. Add a `raycast` shortcut and `moveAlongSurface` for followers if not using crowd. |
| `NavmeshCache` worker/queue pattern | Pattern only | Remove sqlite, patch and TES logic. |
| `NavmeshDebug` carpet overlay | Yes | Adapt to `ForwardRenderer`; very useful for tuning. |
| `NpcMannequin.followDetour/stepPath/turnToward/stickLand` | Pattern | Replace with Detour crowd agents in phase 6. |
| `PathgridGraph` A* | Optional | Only if you hand-author waypoint graphs. It is OpenMW-derived GPL, so keep its header. |
| `NavmeshDb`, `NavmeshGaps`, `addSeamStitch`, `CollisionMesh` NIF rules, `CollisionTris` | No | Specific to OpenMW/TES. |

**Shared module, later:** once both repos have instance-based `PhysicsWorld`/`KinematicCapsule`/`NavWorld` classes with no game types, move them into a small `jvm-navphys` library. It could live in its own repo consumed through a Gradle composite build (`includeBuild '../jvm-navphys'`) or JitPack, with `api gdx-bullet`, `implementation recast4j`. Both projects are GPL-3.0 with the same owner, so licensing is no obstacle. The cost is retrofitting jvm-mw's static classes. Don't block the crawler on that.

### 3.5 Coordinate-system pitfalls

* **One world frame:** physics, navmesh and actor positions all in **GL Y-up metres**. Convert to the Veloren Z-up skeleton only at the figure root, with one fixed rotation (the same `(x,y,z)→(x,z,−y)` the mesher uses). Never put Z-up positions into Bullet or Recast.
* **Yaw:** with this rotation, a Veloren yaw θ about +Z equals a GL yaw θ about +Y (same sign), so actor yaw can be shared directly. libGDX `Matrix4.rotate`/`Quaternion.set(axis, deg)` take **degrees**, while Veloren and `Math.atan2` use radians (also flagged in the animation write-up).
* **Mirroring flips winding.** Any axis swap or negation with determinant −1 (for example OpenMW's `(x,z,y)` or jvm-mw's `(x,y,−z)`) reverses triangle winding, and Recast then marks floors as unwalkable. If you mirror, reverse the indices too.
* **Detour tile origin** must match the bake origin (§3.3 point 6).
* **Capsule:** `btCapsuleShape(radius, height)` takes the *cylinder* height, so pass `totalHeight − 2·radius`. Sweep the capsule centre at `feet + totalHeight/2`, as jvm-mw does.
* **Half-voxel offsets:** `VoxMesher` centres models (`x − size/2`). Dungeon chunks should use integer block corners in world space, or collision and render will be off by half a block.
* **Threads:** Bullet objects are GL/game-thread only and must be disposed explicitly. recast4j is pure Java and safe on workers. A `NavMeshQuery` must not be used from two threads at once.
* **Natives:** `Bullet.init()` before creating any `bt*` object. Never in headless converter or CLI tasks.

### 3.6 License compatibility (crawler is GPL-3.0)

| Component | License | GPL-3.0 compatible? |
|---|---|---|
| libGDX, gdx-bullet wrapper, gdx-ai | Apache-2.0 | Yes (Apache-2.0 → GPLv3 is compatible; it is *not* compatible with GPLv2-only) |
| Bullet Physics (inside the gdx-bullet natives) | zlib | Yes |
| recast4j (recast, detour, detour-crowd, tile-cache) | zlib | Yes |
| jvm-mw code | GPL-3.0, same owner | Yes. Keep "Ported from OpenMW" headers on OpenMW-derived files (for example `PathgridGraph`). |
| Veloren (`common/src/path.rs`, `common/systems/src/phys/collision.rs` if you port its voxel A* or collision) | GPL-3.0 | Yes. Credit the source file, as with the shader and animation ports. |
| jolt-jni (if ever) | MIT | Yes |

### 3.7 Prioritised plan and estimates

| # | Slice | Effort | Notes |
|---|---|---|---|
| 0 | Decide units (1 unit = 1 m = 1 block), figure scale, world frame; write a short `docs/physics-nav.md` in jvm-mw's spec style | 0.5 d | Blocks everything below |
| 1 | Block volume + chunks + **greedy collision mesher** + a test dungeon (hand-made or loaded from a Veloren dungeon/`.vox` asset) rendered with existing shaders | 1.5–2 d | No physics yet |
| 2 | `PhysicsWorld` (gdx-bullet `btCollisionWorld`, chunk BVH bodies, filter groups, dispose), `Bullet.init`, F7-style collider debug draw | 1 d | Pattern from jvm-mw phases 45–47 |
| 3 | Port the **kinematic capsule** + first-/third-person walk camera (WASD, step 1 block, gravity, ceilings, spawn snap) | 1.5–2 d | Biggest copy from `BulletWorld.move`; tune constants in metres |
| 4 | **Recast tiled bake** from chunk triangles on a worker + F6 carpet overlay + Dump counts | 1.5–2 d | Fixed origin, identity coordinates, `walkableClimb = stepUp` |
| 5 | **Detour queries** + simple path follower for one monster type (`findNearestPoly`/`findPath`/`findStraightPath`, arrive radius, turn rate, ray-down foot snap) | 1–1.5 d | `NavmeshQuery` + `followDetour` patterns |
| 6 | **detour-crowd** for multiple monsters (separation/avoidance, re-path on target move) + actor capsules vs world | 1–2 d | Fills jvm-mw's open §4–5 rows too |
| 7 | Dynamic changes: doors/destructible blocks, chunk body rebuild + affected-tile rebake via `updateTile` | 1–2 d | |
| 8 | Optional: extract `jvm-navphys` shared module and move jvm-mw onto it (also fixes jvm-mw bugs #1–#3 for both) | 1.5–3 d | Only after both repos have it working |

**Core walkable dungeon with one pathing monster (0–5): about 7–9 days. With crowd and dynamic doors (6–7): about 9–13 days.** Per-body animation (from the animation report) can be built alongside: phase 5's follower drives the Veloren run/idle blend using speed along the path.

**Lighter alternative (no JNI), if levels are strictly block-aligned:** a Veloren-style voxel AABB/cylinder sweep (`phys/collision.rs` `box_voxel_collision`) plus Veloren's block A* (`path.rs` `walkable`/`find_path`, or gdx-ai `IndexedAStarPathFinder`). That is about 3–5 days for 1–5, but nothing is shared with jvm-mw, and you'd write your own string-pulling and agent-radius handling. I'd only pick it if you want to drop the Bullet natives.

---

## 4. Things to verify before implementing

* jvm-mw bug #2 (winding in TES-space patch tiles): check the F6 carpet on a dock inside a patched tile.
* recast4j 1.5.12 compiles unchanged against jvm-mw-style code (`Result<>` wrappers, `RecastConfig` constructor arity). I checked that the class exists but did not compile against it.
* gdx-ai 1.8.2 (2019) with libGDX 1.14: pathfinding and btree are fine in practice, but smoke-test if you adopt it.
